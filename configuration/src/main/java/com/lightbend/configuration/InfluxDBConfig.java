package com.lightbend.configuration;

import com.typesafe.config.Config;

public class InfluxDBConfig {
    public final String host;
    public final String port;
    public final String user = "root";
    public final String pass = "root";
    public final String database = "serving";
    public final String retentionPolicy = "default";

    public InfluxDBConfig(Config config) {
        host = config.getString("host");
        port = config.getString("port");
    }

    public String url() {
        return host+":"+port;
    }

    public String toString() {
        return "host ["+host+"], port ["+port+"]";
    }
}
