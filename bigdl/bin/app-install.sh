#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

HELP_MESSAGE="Installs the BigDL sample app. Assumes DC/OS authentication was successful
  using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS=

: ${S3_BUCKET:=fdp-sample-bigdl-vgg}

function parse_arguments {

  while :; do
    case "$1" in
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit 0
      ;;
      -n|--no-exec)   # Don't actually run the installation commands; just print (for debugging)
      NOEXEC="echo running: "
      ;;
      --stop-at)      # for testing
        shift
        stop_point=$1
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
function generate_app_uninstall_metadata {
declare METADATA=$(cat <<EOF
{
  "BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID":""
}
EOF
)
  echo "$METADATA" > $APP_METADATA_FILE
}

function run_bigdl_vgg_job {
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.bigdl.TrainVGG"
  local SPARK_CONF="--conf spark.cores.max=2 --conf spark.executorEnv.OMP_NUM_THREADS=1 --conf spark.executorEnv.KMP_BLOCKTIME=0 --conf OMP_WAIT_POLICY=passive --conf DL_ENGINE_TYPE=mklblas --conf spark.executor.memory=8G --driver-memory 4G"
  local core_number_per_node=4
  local node_number=1
  local ARGS="--core $core_number_per_node --node $node_number --env spark -f cifar-10-batches-bin -b 16"
  local SPARK_VGG_APP_JAR="bigdlsample-assembly-0.0.1.jar"
  local SPARK_APP_JAR_URL="http://$S3_BUCKET.s3.amazonaws.com/$SPARK_VGG_APP_JAR"
  local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"

  BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
  $NOEXEC update_json_field BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
  show_submission_message "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID"
}

function main {

  parse_arguments "$@"

  # remove metadata file
  $NOEXEC rm -f "$APP_METADATA_FILE"

  header "Verifying required tools are installed...\n"

  require_dcos_cli

  require_spark

  require_jq

  require_auth

  header "Generating metadata for subsequent uninstalls...\n"

  generate_app_uninstall_metadata

  header "Running the BigDL VGG application... "
  run_bigdl_vgg_job
}

main "$@"
exit 0
