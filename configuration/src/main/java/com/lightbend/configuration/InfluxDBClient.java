package com.lightbend.configuration;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import java.util.concurrent.TimeUnit;

public class InfluxDBClient {

    private InfluxDB influxDB;

    public InfluxDBClient(InfluxDBConfig config) {
        influxDB = InfluxDBFactory.connect(config.url(),config.user, config.pass);
        if(!influxDB.databaseExists(config.database)){
            influxDB.createDatabase(config.database);
            influxDB.dropRetentionPolicy("autogen", config.database);
            influxDB.createRetentionPolicy(config.retentionPolicy, config.database,
                    "1d", "30m", 1, true);
        }

        influxDB.setDatabase(config.database);
        // Flush every 2000 Points, at least every 100ms
        influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS);
        // set retention policy
        influxDB.setRetentionPolicy(config.retentionPolicy);
    }

    public void writePoint(String engine, String model, double calculated, double duration){
        Point point = Point.measurement("serving")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("calculated", calculated)
                .addField("duration", duration)
                .tag("engine", engine)
                .tag("model", model)
                .build();
        influxDB.write(point);
    }
}