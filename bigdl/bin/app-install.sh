#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

: ${S3_BUCKET:=fdp-sample-bigdl-vgg}

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
  local SUBMIT="$(dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"

  BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
  update_json_field BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
  show_submission_message "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID"
}

function main {

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
