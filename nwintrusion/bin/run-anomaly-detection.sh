#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/common.sh"

# Used by show_help
HELP_MESSAGE="Runs the anomaly detection app that uses Spark Streaming."
HELP_EXAMPLE_OPTIONS="-k 5"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  -k | --number-of-clusters K Starting number of clusters to try.
                              (default: $DEFAULT_NO_OF_CLUSTERS)
  --mb | --micro-batch-duration
                              Spark micro batch duration (seconds)
                              (default: $DEFAULT_CLUSTERING_MICRO_BATCH_DURATION)
  -j | --spark-job-s3-bucket
                              The S3 bucket where the spark job jar can be found
                              (default: http://fdp-kdd-network-intrusion.s3.amazonaws.com)
EOF
)

no_of_clusters=$DEFAULT_NO_OF_CLUSTERS
micro_batch_duration=$DEFAULT_CLUSTERING_MICRO_BATCH_DURATION
spark_job_s3_bucket="http://fdp-kdd-network-intrusion.s3.amazonaws.com"

function parse_arguments {

  while :; do
    case "$1" in
      -k|--number*)
      shift
      no_of_clusters=$1
      ;;
      --mb|--micro*)
      shift
      micro_batch_duration=$1
      ;;
      --j|--spark-job-s3-bucket*)
      shift
      spark_job_s3_bucket=$1
      ;;
      --with-iam-role)
      WITH_IAM_ROLE="TRUE"
      shift 1
      continue
      ;;
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit 0
      ;;
      -n|--no-exec)   # Don't actually run the installation commands; just print (for debugging)
      NOEXEC="echo running: "
      ;;
      --)              # End of all options.
      shift
      break
      ;;
      '')              # End of all options.
      break
      ;;
      *)
      error "The option is not valid: $1"
      ;;
    esac
    shift
  done
}

function main {

  parse_arguments "$@"

  require_dcos_cli

  require_spark

  require_auth

  gather_kafka_connection_info

  echo "Running the spark application for anomaly detection:"
  echo "  k = $no_of_clusters"
  echo "  micro batch duration = $micro_batch_duration seconds"
  echo "  getting spark job jar from: $spark_job_s3_bucket"

  run_anomaly_detection_spark_job $no_of_clusters $micro_batch_duration $spark_job_s3_bucket
}

main "$@"
exit 0
