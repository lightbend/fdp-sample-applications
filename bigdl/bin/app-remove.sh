#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../version.sh"
. "$DIR/../../bin/common.sh"

# Used by show_help
HELP_MESSAGE="Removes BigDL VGG training Spark app from the current cluster"
HELP_EXAMPLE_OPTIONS=

function parse_arguments {

  while :; do
    case "$1" in
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit
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
      printf 'The option is not valid...: %s\n' "$1" >&2
      show_help
      exit 1
      ;;
    esac
    shift
  done
}

BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID="$(jq -r '.BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID' $APP_METADATA_FILE)"

function main {

  parse_arguments "$@"
  if [ ! -f "$APP_METADATA_FILE" ]; then
    echo "$APP_METADATA_FILE is missing or is not a file."
    exit 1
  fi

  if [ -n "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID" ]; then
    echo "Deleting spark driver with id: $BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID"
    $NOEXEC dcos spark kill $BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID
  else
    echo "No Spark Driver ID for BigDL VGG is available. Skipping delete.."
  fi

}

main "$@"
exit 0
