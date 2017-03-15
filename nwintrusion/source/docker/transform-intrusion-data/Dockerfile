FROM java:openjdk-8-jre

ADD fdp-nw-intrusion-assembly-0.1.jar /app/fdp-nw-intrusion-assembly-0.1.jar
ENTRYPOINT ["java", "-cp", "\/app\/fdp-nw-intrusion-assembly-0.1.jar", "com.lightbend.fdp.sample.TransformIntrusionData"]
