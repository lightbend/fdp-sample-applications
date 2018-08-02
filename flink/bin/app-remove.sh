#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../version.sh"
. "$DIR/../../bin/common.sh"

INGESTION_DATA_APP_ID="$(jq -r '.INGESTION_DATA_APP_ID' $APP_METADATA_FILE)"
APP_DATA_APP_ID="$(jq -r '.APP_DATA_APP_ID' $APP_METADATA_FILE)"
KAFKA_DCOS_PACKAGE="$(jq -r '.KAFKA_DCOS_PACKAGE' $APP_METADATA_FILE)"
KAFKA_DCOS_SERVICE_NAME="$(jq -r '.KAFKA_DCOS_SERVICE_NAME' $APP_METADATA_FILE)"
topics="$(jq -r '.TOPICS[]' $APP_METADATA_FILE)"

# Used by show_help
HELP_MESSAGE="Removes the NYC Taxi Ride app from the current cluster"
HELP_EXAMPLE_OPTIONS=

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  --skip-delete-topics        Skip deleting the topics.
EOF
)

function parse_arguments {

  while :; do
    case "$1" in
      --skip-delete-topics)
        SKIP_DELETE_TOPICS="TRUE"
        shift 1
        continue
      ;;
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


function main {

  parse_arguments "$@"
  if [ ! -f "$APP_METADATA_FILE" ]; then
    echo "$APP_METADATA_FILE is missing or is not a file."
    exit 1
  fi

  if [ -n "$INGESTION_DATA_APP_ID" ]; then
    echo "Deleting app with id: $INGESTION_DATA_APP_ID"
    $NOEXEC dcos marathon app remove --force $INGESTION_DATA_APP_ID
  else
    echo "No LOAD APP ID is available. Skipping delete.."
  fi

  if [ -n "$APP_DATA_APP_ID" ]; then
    echo "Deleting app with id: $APP_DATA_APP_ID"
    $NOEXEC dcos marathon app remove --force $APP_DATA_APP_ID
  else
    echo "No LOAD APP ID is available. Skipping delete.."
  fi

  if [ -z $SKIP_DELETE_TOPICS ]; then
    if [ -n "$KAFKA_DCOS_PACKAGE" ]; then
      for elem in $topics
      do
        echo "Deleting topic $elem..."
        $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic delete $elem --name="$KAFKA_DCOS_SERVICE_NAME"
      done
    else
      echo "KAFKA_DCOS_PACKAGE is not defined in $APP_METADATA_FILE. Skipping topic deletion.."
    fi
  else
    echo "Not deleting Kafka topics: $topics"
  fi
}

main "$@"
exit 0

