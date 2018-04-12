package com.lightbend.modelserver.withstore;

import com.lightbend.configuration.AppConfig;
import com.lightbend.configuration.AppParameters;
import com.lightbend.modelserver.store.ModelStateStoreBuilder;
import com.lightbend.queriablestate.QueriesRestService;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


@SuppressWarnings("Duplicates")
public class ModelServerWithStore {

    public static void main(String [ ] args) throws Throwable {

        System.out.println("Kafka Streams Model server: "+AppConfig.stringify());

        Properties streamsConfiguration = new Properties();
        // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
        // against which the application is run.
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-model-server-example");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "kafka-model-server-client");
        // Where to find Kafka broker(s).
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.KAFKA_BROKER);
        // Provide the details of our embedded http service that we'll use to connect to this streams
        // instance and discover locations of stores.
        streamsConfiguration.put(StreamsConfig.APPLICATION_SERVER_CONFIG, "localhost:" + AppConfig.QUERIABLE_STATE_PORT);
        // Default serdes
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());

        // Create topology
        final KafkaStreams streams = createStreams(streamsConfiguration);
        streams.cleanUp();
        streams.start();
        // Start the Restful proxy for servicing remote access to state stores
        final QueriesRestService restService = startRestProxy(streams, AppConfig.QUERIABLE_STATE_PORT);
        // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                streams.close();
                restService.stop();
            } catch (Exception e) {
                // ignored
            }
        }));
    }

    static KafkaStreams createStreams(final Properties streamsConfiguration) {

        // Create model store
        Map<String, String> logConfig = new HashMap<>();
        ModelStateStoreBuilder storeBuilder = new ModelStateStoreBuilder("modelStore").withLoggingEnabled(logConfig);

        // Create topology
        Topology topology = new Topology();

        // Data input streams
        topology.addSource("data", AppParameters.DATA_TOPIC)
                .addProcessor("ProcessData", DataProcessorWithStore::new, "data");
        topology.addSource("models",AppParameters.MODELS_TOPIC)
                .addProcessor("ProcessModels", ModelProcessorWithStore::new, "models");
        topology.addStateStore(storeBuilder,"ProcessData", "ProcessModels");

        return new KafkaStreams(topology, streamsConfiguration);
    }

    static QueriesRestService startRestProxy(final KafkaStreams streams, final int port) throws Exception {
        final QueriesRestService restService = new QueriesRestService(streams);
        restService.start(port);
        return restService;
    }
}
