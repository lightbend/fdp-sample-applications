#!/usr/bin/env bash

# for debug
# set -e

# For multi-line output, define an array of strings, e.g.,
#   msg=("line one" "line two")
# and pass the array as follows:
#   error ${msg[@]}
# Using `error $msg` won't print properly!
function error {
  echo >&2
  echo "ERROR: " >&2
  for s in "$@"
  do
    echo "  $s" >&2
    shift
  done
  echo >&2
  usage()
  exit 1
}

usage() {
  cat <<EOF
  Usage: $0 [options]
    -b | --broker-list   Kafka Brokers (e.g. "10.10.1.161:9092,10.10.1.159:9092,10.10.1.160:9092")
    -t | --topic         Kafka Topic (e.g. githublog)
    -z | --zookeeper     Zookeeper URL (e.g. 10.10.1.161:2181/kafka)

EOF
  exit 1
}

# parameters from command line
if [[ $# != 6 ]]
then
  error "Invalid number of parameters"
fi

# process keyword based arguments
while [[ $# -gt 1 ]]
  do
  key="$1"
  
  case $key in
      -b|--broker-list)
      KAFKA_BROKERS="$2"
      shift # past argument
      ;;
      -z|--zookeeper)
      ZOOKEEPER="$2"
      shift # past argument
      ;;
      -t|--topic)
      KAFKA_TOPIC="$2"
      shift # past argument
      ;;
      --default)
      DEFAULT=YES
      ;;
      *)
              # unknown option
      ;;
  esac
  shift # past argument or value
done

echo $KAFKA_BROKERS
echo $ZOOKEEPER
echo $KAFKA_TOPIC

# properties with defaults. Can be overwritten if needed.

KAFKA_PRODUCER_CMD=${KAFKA_PRODUCER_CMD:-/bin/kafka-console-producer.sh}
KAFKA_TOPIC_CMD=${KAFKA_TOPIC_CMD:-/bin/kafka-topics.sh}

TMP_FOLDER=ingest-taxidata-tmp

# check kafka-console-producer is available
if [ ! -x $KAFKA_PRODUCER_CMD ]
then
  error "Didn't find an executable for Kafka producer at: $KAFKA_PRODUCER_CMD"
fi

# check kafka-topic command is available
if [ ! -x $KAFKA_TOPIC_CMD ]
then
  error "Didn't find an executable for Kafka topic at: $KAFKA_TOPIC_CMD"
fi

#check if the actual topic is present
$KAFKA_TOPIC_CMD --describe --zookeeper $ZOOKEEPER --topic $KAFKA_TOPIC | grep $KAFKA_TOPIC
if [ $? != 0 ]
then
  error "Topic $KAFKA_TOPIC does not exist .. Create the topic before running this script"
fi

# clean-up previous tmp files
rm -rf $TMP_FOLDER
mkdir -p $TMP_FOLDER

S3_BUCKET_URL="http://fdp-sample-flink-taxirides-new.s3.amazonaws.com"
S3_FILE_NAME="nycTaxiRides.csv.gz"
if curl --output /dev/null --silent --head --fail "$S3_BUCKET_URL/$S3_FILE_NAME"; then
  echo "Got file: $S3_FILE_NAME"
else
  error "$S3_FILE_NAME does not exist in $S3_BUCKET_URL"
fi

cd $TMP_FOLDER

curl "$S3_BUCKET_URL/$S3_FILE_NAME" | gunzip | $KAFKA_PRODUCER_CMD --broker-list "$KAFKA_BROKERS" --topic "$KAFKA_TOPIC"
# cat "$S3_FILE_NAME" | gunzip | $KAFKA_PRODUCER_CMD --broker-list "$KAFKA_BROKERS" --topic "$KAFKA_TOPIC"


