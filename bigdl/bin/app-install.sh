#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../version.sh"
. "$DIR/../../bin/common.sh"

## project root directory
PROJ_ROOT_DIR="$( cd "$DIR/../source/core" && pwd -P )"

DOCKER_USERNAME=lightbend
BIGDL_VGG_DOCKER_IMAGE=bigdlvgg
BIGDL_VGG_JAR=bigdlvgg-assembly

HELP_MESSAGE="Installs the BigDL sample app. Assumes DC/OS authentication was successful
  using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS=

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

  local SPARK_CONF="--conf spark.mesos.appJar.local.resolution.mode=container --conf spark.cores.max=2 --conf spark.executor.cores=2 --conf spark.executorEnv.OMP_NUM_THREADS=1 --conf spark.executorEnv.KMP_BLOCKTIME=0 --conf OMP_WAIT_POLICY=passive --conf DL_ENGINE_TYPE=mklblas --conf spark.executor.memory=8G --conf spark.mesos.executor.docker.image=$DOCKER_USERNAME/$BIGDL_VGG_DOCKER_IMAGE:$VERSION --driver-memory 4G"

  local ARGS="-f /tmp/cifar-10-batches-bin --download /tmp -b 16"

  echo dcos spark run --name=spark --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS local:///opt/spark/dist/jars/$BIGDL_VGG_JAR-$VERSION.jar $ARGS"

  local SUBMIT="$($NOEXEC dcos spark run --name=spark --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS local:///opt/spark/dist/jars/$BIGDL_VGG_JAR-$VERSION.jar $ARGS")"

  if [ -z "$NOEXEC" ]
    then
      BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
      $NOEXEC update_json_field BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
      show_submission_message "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID"
    else
      echo "$SUBMIT"
  fi
}

function main {

  parse_arguments "$@"

  $NOEXEC rm -f "$APP_METADATA_FILE"

  header "Verifying required tools are installed...\n"

  require_dcos_cli

  require_spark

  require_jq

  header "Generating metadata for subsequent uninstalls...\n"

  generate_app_uninstall_metadata

  header "Running the BigDL VGG application... "
  run_bigdl_vgg_job
}

main "$@"
exit 0
