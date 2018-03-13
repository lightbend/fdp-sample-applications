package com.lightbend.configuration;

import org.apache.commons.codec.binary.Base64;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GrafanaClient {

    private static String dsfile = "/grafana-source.json";
    private static String dashfile = "/grafana-dashboard.json";

    public GrafanaClient(GrafanaConfig grafanaConfig, InfluxDBConfig influxDBConfig) {
        // Make sure Grafana is set up
        String authString = grafanaConfig.user + ":" + grafanaConfig.pass;
        String authStringEnc = new String(Base64.encodeBase64(authString.getBytes()));

        try {
            // Source
            String source = "http://" + grafanaConfig.url() + "/api/datasources";
            URL url = new URL(source);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // Setting basic post request
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Basic " + authStringEnc);

            // Send Grafana source json

            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(dsfile)));
            String line = null;
            while((line = in.readLine()) != null) {
                if(line.contains("\"url\""))
                    line = " \"url\": \"" + influxDBConfig.url() + "\",";
                wr.write(line.getBytes());
            }
            wr.flush();
            wr.close();
            System.out.println("Uploaded Grafana source");
            printResponse(con);

            String dashboard = "http://" + grafanaConfig.url() + "/api/dashboards/db";
            url = new URL(dashboard);
            con = (HttpURLConnection) url.openConnection();

            // Setting basic post request
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Basic " + authStringEnc);

            // Send post request
            con.setDoOutput(true);
            wr = new DataOutputStream(con.getOutputStream());
            streamData(this.getClass().getResourceAsStream(dashfile), wr);
            System.out.println("Uploaded Grafana dashboard");
            printResponse(con);

        }
        catch (Throwable t){
            t.printStackTrace();
        }

    }

    private static void printResponse(HttpURLConnection con){
        try {
            int responseCode = con.getResponseCode();
            System.out.println("Response Code : " + responseCode);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String output;
            StringBuffer response = new StringBuffer();

            while ((output = in.readLine()) != null) {
                response.append(output);
            }
            in.close();

            //printing result from response
            System.out.println("Response message" + response.toString());
        }
        catch (Throwable t){}
    }

    private static void streamData(InputStream in, DataOutputStream out){
        try {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
        }
        catch (Throwable t){}
    }


}
