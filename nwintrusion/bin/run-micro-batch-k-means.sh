#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/utils.sh"

# Used by show_help
HELP_MESSAGE="Runs the micro batch K-Means app for determining a good K value."
HELP_EXAMPLE_OPTIONS="--start 1 --end 10 --delta 2"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  -s | --start | --start-number-of-clusters K
                              Starting number of clusters to try.
                              (default: $DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT)
  -e | --end | --end-number-of-clusters K
                              Ending number of clusters to try (inclusive).
                              (default: $DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT)
  -d   | --delta n            Delta between trials for Ks.
  --mb | --micro-batch-duration N
                              Spark micro batch duration (seconds)
                              (default: $DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION)
  -j | --spark-job-s3-bucket
                              The S3 bucket where the spark job jar can be found
                              (default: http://fdp-kdd-network-intrusion.s3.amazonaws.com)
EOF
)

start_no_of_clusters=$DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT
end_no_of_clusters=$DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT
delta_no_of_clusters=$DEFAULT_OPTIMAL_K_INCREMENT
micro_batch_duration=$DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION
spark_job_s3_bucket="http://fdp-kdd-network-intrusion.s3.amazonaws.com"

function parse_arguments {

  while :; do
    case "$1" in
      -s|--start*)
      shift
      start_no_of_clusters=$1
      ;;
      -e|--end*)
      shift
      end_no_of_clusters=$1
      ;;
      -d|--d*)
      shift
      delta_no_of_clusters=$1
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

  echo "Running the spark application for optimizing K for K-Means:"
  echo "  Trying K between $start_no_of_clusters and $end_no_of_clusters (inclusive)"
  echo "  Delta between K values = $delta_no_of_clusters"
  echo "  micro batch duration = $micro_batch_duration"
  echo "  getting spark job jar from: $spark_job_s3_bucket"

  run_batch_kmeans_spark_job $start_no_of_clusters $end_no_of_clusters $delta_no_of_clusters $micro_batch_duration $spark_job_s3_bucket
}

main "$@"
exit 0
