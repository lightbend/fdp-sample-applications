#!/usr/bin/env bash
set -e

# ingest data to Kafka topic from the data file
/bin/load-intrusion-data.sh --broker-list $KAFKA_BROKERS --topic $KAFKA_FROM_TOPIC --zookeeper $ZOOKEEPER_URL
