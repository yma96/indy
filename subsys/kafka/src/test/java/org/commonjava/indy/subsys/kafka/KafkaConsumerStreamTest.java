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
package org.commonjava.indy.subsys.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.commonjava.event.common.EventMetadata;
import org.commonjava.event.store.StoreEventType;
import org.commonjava.event.store.StorePostUpdateEvent;
import org.commonjava.event.store.StoreUpdateType;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.commonjava.indy.subsys.kafka.event.DefaultIndyStoreEvent;
import org.commonjava.indy.subsys.kafka.handler.RepoServiceEventHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Properties;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;

public class KafkaConsumerStreamTest
{
    private final Serde<String> stringSerde = new Serdes.StringSerde();

    RepoServiceEventHandler handler = new RepoServiceEventHandler();

    private TestInputTopic<String, String> inputTopic;

    private TestOutputTopic<String, String> outputTopic;

    private TopologyTestDriver driver;

    private ObjectMapper mapper;

    @Before
    public void setup()
    {
        mapper = new IndyObjectMapper( false );

        final Properties props = new Properties();
        props.put( StreamsConfig.APPLICATION_ID_CONFIG, "test-app-id" );
        props.put( StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "test.bootstrap.server" );
        props.put( StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, stringSerde.getClass().getName() );
        props.put( StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, stringSerde.getClass().getName() );

        final StreamsBuilder builder = new StreamsBuilder();
        builder.stream( "input" ).to( "output" );
        final Topology topology = builder.build();
        driver = new TopologyTestDriver( topology, props );

        inputTopic = driver.createInputTopic( "input", stringSerde.serializer(), stringSerde.serializer() );
        outputTopic = driver.createOutputTopic( "output", stringSerde.deserializer(), stringSerde.deserializer() );
    }

    @Test
    public void test() throws JsonProcessingException
    {
        EventMetadata eventMetadata = new EventMetadata();
        eventMetadata.setPackageType( "maven" );
        eventMetadata.set( "change-summary", "Change Summary" );
        StorePostUpdateEvent storePostUpdateEvent =
                        new StorePostUpdateEvent( StoreUpdateType.UPDATE, eventMetadata, new HashMap<>() );
        storePostUpdateEvent.setEventType( StoreEventType.PostUpdate );

        inputTopic.pipeInput( mapper.writeValueAsString( storePostUpdateEvent ) );

        String output = outputTopic.readValue();
        assertNotNull( output );

        DefaultIndyStoreEvent storeEvent = mapper.readValue( output, DefaultIndyStoreEvent.class );
        org.commonjava.maven.galley.event.EventMetadata metadata = handler.convertEventMetadata( storeEvent );

        assertThat( metadata.getPackageType(), equalTo( "maven" ) );
        assertThat( metadata.getMetadata().get( "change-summary" ), equalTo( "Change Summary" ) );
        assertThat( outputTopic.isEmpty(), is( true ) );
    }

    @After
    public void tearDown()
    {
        driver.close();
    }
}
