#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

APP_METADATA_FILE="$DIR/app.metadata.json"
BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID="$(jq -r '.BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID' $APP_METADATA_FILE)"

function main {

  if [ ! -f "$APP_METADATA_FILE" ]; then
    echo "$APP_METADATA_FILE is missing or is not a file."
    exit 1
  fi

  if [ -n "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID" ]; then
    echo "Deleting spark driver with id: $BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID"
    dcos spark kill $BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID
  else
    echo "No Spark Driver ID for BigDL VGG is available. Skipping delete.."
  fi

}

main "$@"
exit 0
