from intelanalytics/analytics-zoo:0.1.0-spark-2.2.0
WORKDIR /opt/work

RUN apt-get install -y python-tk

# generated Python script from Jupyter Notebook
ADD ./dnn_anomaly_bigdl.py /opt/work
ADD ./wait-and-start-training.sh /opt/work/analytics-zoo/scripts

# data folder : to be mapped as volume during run
RUN mkdir data
