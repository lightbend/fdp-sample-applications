package com.lightbend.configuration;

import com.typesafe.config.Config;

public class GrafanaConfig {
    public final String host;
    public final String port;
    public final String user = "admin";
    public final String pass = "admin";

    public GrafanaConfig(Config config) {
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
