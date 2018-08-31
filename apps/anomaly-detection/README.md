# Anomaly Detection

This is a repo for all supporting applications for Anomaly detection (leveraging [Intel BigDL](https://software.intel.com/en-us/articles/bigdl-distributed-deep-learning-on-apache-spark) for machine learning)
This includes:

* Data publisher - publishing data simulating CPU signal to kafka
* Data collector - getting data from Kafka and storing it to InfluxDB for used by machine learning and visualization
* Model serving - serving models in real time and updating them as new models will become available. Its based on the [minibook](https://www.lightbend.com/blog/serving-machine-learning-models-free-oreilly-ebook-from-lightbend) 
* Speculative Model serving - serving models, leveraging speculative execution and voting decision making, in real time and updating them as new models will become available. Based on this [blog post](https://developer.lightbend.com/blog/2018-05-24-speculative-model-serving/index.html)
* ...... 

