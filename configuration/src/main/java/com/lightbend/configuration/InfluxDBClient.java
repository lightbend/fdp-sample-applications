package com.lightbend.configuration;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class InfluxDBClient {

    private InfluxDB influxDB;

    public InfluxDBClient(InfluxDBConfig config) {
        influxDB = InfluxDBFactory.connect(config.url(),config.user, config.pass);
        Query databasesQuery = new Query("SHOW DATABASES","");

        Boolean database_exists = false;
        List<List<Object>> databases = influxDB.query(databasesQuery).getResults().get(0).getSeries().get(0).getValues();
        if(databases != null){
            for(List<Object> nl : databases){
                if(config.database.equals(nl.get(0).toString())){
                    database_exists = true;
                    break;
                }
            }
        }

        if(!database_exists){
            Query databaseCreateQuery = new Query("CREATE DATABASE \"" + config.database + "\"","");
            influxDB.query(databaseCreateQuery);
            Query dropRetentionQuery = new Query("DROP RETENTION POLICY \"autogen\" ON \"" + config.database + "\"","");
            influxDB.query(dropRetentionQuery);
            Query createRetentionQuery = new Query("CREATE RETENTION POLICY \"" + config.retentionPolicy + "\" ON \"" + config.database + "\" DURATION 1d REPLICATION 1 SHARD DURATION 30m  DEFAULT","");
            influxDB.query(createRetentionQuery);
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