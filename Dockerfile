FROM gcr.io/ynli-k8s/spark:v2.3.0

RUN mkdir -p /opt/spark/jars
COPY killrweather-app/target/scala-2.11/killrWeatherApp-assembly-1.1.0.jar /opt/spark/jars
RUN mkdir -p /etc/hadoop/conf
RUN export HADOOP_CONF_DIR=/etc/hadoop/conf