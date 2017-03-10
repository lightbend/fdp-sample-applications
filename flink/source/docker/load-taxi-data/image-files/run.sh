#!/usr/bin/env bash
set -e

# ingest data to Kafka topic from the data file
/bin/load-nyc-taxidata.sh --broker-list $KAFKA_BROKERS --topic $KAFKA_FROM_TOPIC --zookeeper $ZOOKEEPER_URL

