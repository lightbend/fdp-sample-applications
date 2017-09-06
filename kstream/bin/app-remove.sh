#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

# Used by show_help
HELP_MESSAGE="Removes the kafka streams dsl app from the current cluster."
HELP_EXAMPLE_OPTIONS=

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  --skip-delete-topics        Skip deleting the topics.
  --stop-only X               Only stop the following apps:
                                dsl         Stops topology based on Kafka Streams DSL
                                procedure   Stops topology that implements custom state repository based on Kafka Streams procedures
                              Repeat the option to run more than one.
                              Default: stops all of them
EOF
)

APP_METADATA_FILE_DSL="$DIR/app.metadata.dsl.json"
APP_METADATA_FILE_PROC="$DIR/app.metadata.proc.json"

export stop_dsl=
export stop_proc=
apps_selected=

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
      --stop*)
      apps_selected=yes
      shift
      case $1 in
        dsl)        stop_dsl=yes   ;;
        procedure)  stop_proc=yes  ;;
        *) error "Unrecognized value for --stop-only: $1" ;;
      esac
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
  if [ -z "$apps_selected" ]
  then
    stop_dsl=yes
    stop_proc=yes
  fi
}


function main {

  parse_arguments "$@"

  if [ -n "$stop_dsl" ]
  then
    KSTREAM_DSL_APP_ID="$(jq -r '.KSTREAM_DSL_APP_ID' $APP_METADATA_FILE_DSL)"
    KAFKA_DCOS_PACKAGE="$(jq -r '.KAFKA_DCOS_PACKAGE' $APP_METADATA_FILE_DSL)"
    topics="$(jq -r '.TOPICS[]' $APP_METADATA_FILE_DSL)"

    if [ ! -f "$APP_METADATA_FILE_DSL" ]; then
      echo "$APP_METADATA_FILE_DSL is missing or is not a file."
      exit 1
    fi
  
    if [ -n "$KSTREAM_DSL_APP_ID" ]; then
      echo "Deleting app with id: $KSTREAM_DSL_APP_ID"
      $NOEXEC dcos marathon app remove --force $KSTREAM_DSL_APP_ID
    else
      echo "No KSTREAM_DSL APP ID is available. Skipping delete.."
    fi

    if [ -z $SKIP_DELETE_TOPICS ]; then
      if [ -n "$KAFKA_DCOS_PACKAGE" ]; then
        for elem in $topics
        do
          echo "Deleting topic $elem..."
          $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic delete $elem
        done

	## delete stateful streaming topics created by Kafka
	for elem in $(dcos $KAFKA_DCOS_PACKAGE topic list | grep "kstream-weblog-processing" | cut -d"," -f1 | tr -d \") 
	do 
          echo "Deleting topic $elem..."
	  $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic delete $elem 
        done

      else
        echo "KAFKA_DCOS_PACKAGE is not defined in $APP_METADATA_FILE_DSL. Skipping topic deletion.."
      fi
    else
      echo "Not deleting Kafka topics: $topics"
    fi
  fi

  if [ -n "$stop_proc" ]
  then
    KSTREAM_PROC_APP_ID="$(jq -r '.KSTREAM_PROC_APP_ID' $APP_METADATA_FILE_PROC)"
    KAFKA_DCOS_PACKAGE="$(jq -r '.KAFKA_DCOS_PACKAGE' $APP_METADATA_FILE_PROC)"
    topics="$(jq -r '.TOPICS[]' $APP_METADATA_FILE_PROC)"

    if [ ! -f "$APP_METADATA_FILE_PROC" ]; then
      echo "$APP_METADATA_FILE_PROC is missing or is not a file."
      exit 1
    fi
  
    if [ -n "$KSTREAM_PROC_APP_ID" ]; then
      echo "Deleting app with id: $KSTREAM_PROC_APP_ID"
      $NOEXEC dcos marathon app remove --force $KSTREAM_PROC_APP_ID
    else
      echo "No KSTREAM_PROC APP ID is available. Skipping delete.."
    fi

    if [ -z $SKIP_DELETE_TOPICS ]; then
      if [ -n "$KAFKA_DCOS_PACKAGE" ]; then
        for elem in $topics
        do
          echo "Deleting topic $elem..."
          $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic delete $elem
        done

	## delete stateful streaming topics created by Kafka
	for elem in $(dcos $KAFKA_DCOS_PACKAGE topic list | grep "kstream-log-count" | cut -d"," -f1 | tr -d \") 
	do 
          echo "Deleting topic $elem..."
	  $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic delete $elem 
        done

      else
        echo "KAFKA_DCOS_PACKAGE is not defined in $APP_METADATA_FILE_DSL. Skipping topic deletion.."
      fi
    else
      echo "Not deleting Kafka topics: $topics"
    fi
  fi

  echo "Both the applications involve stateful streaming. Hence many stateful topics are created"
  echo "in the process. This program removes all of them. But despite removing the topics, state"
  echo "information will be still there in the local filesystem that needs manual cleanup. Please"
  echo "take a look at https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool"
  echo "for details."

}

main "$@"
exit 0

