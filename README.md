# FDP Sample Applications

Contains sample applications for Lightbend Fast Data Platform (FDP).

> **Disclaimer:** These sample applications are provided as-is, without warranty. They are intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but they have not gone through a robust validation process, nor do they use all the techniques commonly employed for highly-resilient, production applications. Please use them with appropriate caution.

The applications are organized in various folders with each of them containing details of how to use and install as part of your environment. Here's the list of the applications:

* [KillrWeather](apps/killrweather/README.md): KillrWeather is a reference application adopted from Datastax's [version](https://github.com/killrweather/killrweather), showing how to easily leverage and integrate Apache Spark, Apache Cassandra, Apache Kafka, Akka, and InfluxDB for fast, streaming computations. This application focuses on the use case of time series data.

* [Model Server](apps/akka-kafka-streams-model-server/README.md): A sample application that demonstrates one way to update and serve machine learning models in a streaming context, using either Akka Streams or Kafka Streams.

* [Network Intrusion](apps/nwintrusion/README.md): A network intrusion detector application that ingests data from Kafka, runs online clustering algorithm using Spark Streaming to determine anomalies in network data.

* [VGG Training on cifar-10 data using BigDL](apps/bigdl/README.md): This is a demonstration of using a Spark based deep learning library on Fast Data Platform. We use [Intel BigDL](https://github.com/intel-analytics/BigDL) library and train a VGG Network on Cifar-10 data set.

* [Taxiride Application using Flink](apps/flink/README.md): This is an adaptation of the publicly available [Flink training example from dataArtisans](http://training.data-artisans.com/). The application uses Flink as the streaming platform to train a regression classifier that predicts taxi travel time on a New York city data set.

* [Kafka Streams UseCases](apps/kstream/README.md): This example uses Kafka Streams APIs to process weblogs. It shows the power of both the higher level DSLs as well as the lower level Processor based APIs.

