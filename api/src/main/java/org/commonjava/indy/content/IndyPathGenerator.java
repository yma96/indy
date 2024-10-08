/**
 * Copyright (C) 2011-2023 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.content;

import org.commonjava.indy.model.core.PathStyle;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.galley.KeyedLocation;
import org.commonjava.indy.model.util.DefaultPathGenerator;
import org.commonjava.indy.util.LocationUtils;
import org.commonjava.indy.util.PathUtils;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.commonjava.indy.content.DownloadManager.ROOT_PATH;
import static org.commonjava.indy.model.core.PathStyle.base64url;
import static org.commonjava.indy.model.core.PathStyle.hashed;

/**
 * {@link PathGenerator} implementation that assumes the locations it sees will be {@link KeyedLocation}, and translates them into storage locations
 * on disk for related content.
 */
@Default
@ApplicationScoped
public class IndyPathGenerator
        implements PathGenerator
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private Instance<StoragePathCalculator> injectedStoragePathCalculators;

    private Set<StoragePathCalculator> pathCalculators;

    private DefaultPathGenerator defaultPathGenerator = new DefaultPathGenerator();

    public IndyPathGenerator(){}

    public IndyPathGenerator( Set<StoragePathCalculator> pathCalculators )
    {
        this.pathCalculators = pathCalculators;
    }

    @PostConstruct
    public void postConstruct()
    {
        pathCalculators = new HashSet<>();
        if ( !injectedStoragePathCalculators.isUnsatisfied() )
        {
            injectedStoragePathCalculators.forEach( pathCalculators::add );
        }
    }

    @Override
    public String getFilePath( final ConcreteResource resource )
    {
        final KeyedLocation kl = (KeyedLocation) resource.getLocation();
        final StoreKey key = kl.getKey();

        final String name = key.getPackageType() + "/" + key.getType()
                               .name() + "-" + key.getName();

        return PathUtils.join( name, getPath( resource ) );
    }

    @Override
    public String getPath( final ConcreteResource resource )
    {
        final KeyedLocation kl = (KeyedLocation) resource.getLocation();
        final StoreKey key = kl.getKey();
        String path = resource.getPath();
        PathStyle pathStyle = kl.getAttribute(LocationUtils.PATH_STYLE, PathStyle.class);
        if ( hashed == pathStyle || base64url == pathStyle)
        {
            final String rawPath = path;
            path = defaultPathGenerator.getStyledPath( rawPath, pathStyle );
            // Add special handling for generic-http stores' root by removing the trailing '/' from the hashed path
            if ( ROOT_PATH.equals( rawPath ) && path.endsWith( ROOT_PATH ) )
            {
                path = path.substring( 0, path.length() - 1 );
            }
            logger.trace( "Get styled path: {}, rawPath: {}", path, rawPath );
        }
        else
        {
            AtomicReference<String> pathref = new AtomicReference<>( path );
            pathCalculators.forEach( c -> pathref.set( c.calculateStoragePath( key, pathref.get() ) ) );
            path = pathref.get();
        }

        return path;
    }

}
