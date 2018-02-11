package com.lightbend.configuration.kafka;

/**
 * Created by boris on 5/18/17.
 * Set of parameters for running applications
 */
public class ApplicationKafkaParameters {

    private ApplicationKafkaParameters(){}

    public static final String LOCAL_ZOOKEEPER_HOST = getEnv("zookepers","zk-1.zk:2181/dcos-service-kafka");
    public static final String LOCAL_KAFKA_BROKER = getEnv("kafka.brokers","broker.kafka.l4lb.thisdcos.directory:9092");

    public static final String DATA_TOPIC = "models_data";
    public static final String MODELS_TOPIC = "models_models";

    public static final String DATA_GROUP = "wineRecordsGroup";
    public static final String MODELS_GROUP = "modelRecordsGroup";

    // InfluxDB 
    public static final String influxDBServer = getEnv("influxdb.host","http://influxdb.marathon.l4lb.thisdcos.directory");
    public static final String influxDBPort = getEnv("influxdb.port","8086");
    public static final String influxDBUser = "root";
    public static final String influxDBPass = "root";
    public static final String influxDBDatabase = "serving";
    public static final String retentionPolicy = "default";

    // Grafana
    public static final String GrafanaHost = getEnv("grafana.host","grafana.marathon.l4lb.thisdcos.directory");
    public static final String GrafanaPort = getEnv("grafana.port","3000");
    public static final String GrafanaUser = "admin";
    public static final String GrafanaPass = "admin";

    private static String getEnv(String key, String def){
        String value = System.getenv(key);
        return value == null ? def : value;
    }
}
