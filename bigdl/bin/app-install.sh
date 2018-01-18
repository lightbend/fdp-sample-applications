#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

## project root directory
PROJ_ROOT_DIR="$( cd "$DIR/../source/core" && pwd -P )"

## deploy.conf full path
DEPLOY_CONF_FILE="$PROJ_ROOT_DIR/deploy.conf"

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

config_file="$DIR/app-install.properties"

function generate_app_uninstall_metadata {
declare METADATA=$(cat <<EOF
{
  "BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID":""
}
EOF
)
  echo "$METADATA" > $APP_METADATA_FILE
}

keyval() {
  filename=$1
  if [ -f "$filename" ]
  then
    echo "$filename found"

    while IFS='=' read -r key value
    do
      if [ "$key" == "publish-user" ]
      then
        PUBLISH_USER=$value
      fi

      if [ "$key" == "publish-host" ]
      then
        PUBLISH_HOST=$value
      fi

      if [ "$key" == "ssh-port" ]
      then
        SSH_PORT=$value
      fi

      if [ "$key" == "passphrase" ]
      then
        SSH_PASSPHRASE=$value
      fi

      if [ "$key" == "ssh-keyfile" ]
      then
        SSH_KEYFILE=$value
      fi

      if [ "$key" == "laboratory-mesos-path" ]
      then
        LABORATORY_MESOS_PATH=$value
      fi

    done < "$filename"

    if [ -z "${PUBLISH_USER// }" ]
    then
      PUBLISH_USER="publisher"
    fi

    exit_if_not_defined_or_empty "$PUBLISH_HOST" "publish-host"
    exit_if_not_defined_or_empty "$SSH_PORT" "ssh-port"
    exit_if_not_defined_or_empty "$SSH_KEYFILE" "ssh-keyfile"
    exit_if_not_defined_or_empty "$LABORATORY_MESOS_PATH" "laboratory-mesos-path"

  else
    echo "$filename not found."
    exit 6
  fi
}

function exit_if_not_defined_or_empty() {
  value=$1
  name=$2

  if [ -z "${value// }"  ]
  then
    error "$name not defined .. exiting"
  fi
}

function generate_deploy_conf {
declare DEPLOY_CONF_DATA=$(cat <<EOF
{
  servers = [
   {
    name = "fdp-bigdl"
    user = $PUBLISH_USER
    host = $PUBLISH_HOST
    port = $SSH_PORT
    sshKeyFile = $SSH_KEYFILE
   }
  ]
}
EOF
)
  if [[ -z $NOEXEC ]]
  then
    echo "$DEPLOY_CONF_DATA" > "$DEPLOY_CONF_FILE"
  else
    $NOEXEC "$DEPLOY_CONF_DATA > $DEPLOY_CONF_FILE"
  fi
}

function deploy_app {
  $NOEXEC cd "$PROJ_ROOT_DIR"
  $NOEXEC sbt clean clean-files "deploySsh fdp-bigdl"

  if [[ -z $NOEXEC ]]
  then
    ASSEMBLY_JAR_NAME=$( ls "$PROJ_ROOT_DIR"/target/scala-2.11/bigdlsample-assembly-*.jar )
    SPARK_APP_JAR=$( basename "$ASSEMBLY_JAR_NAME" )
  fi
}

## ./spark-submit --master local[4] --conf spark.executor.memory=8G --driver-memory 8G --class com.lightbend.fdp.sample.bigdl.TrainVGG /Users/debasishghosh/lightbend/fdp-sample-apps/bigdl/source/core/target/scala-2.11/bigdlsample-assembly-0.0.2.jar -f /tmp/cifar-10-batches-bin --download /tmp -b 8

function run_bigdl_vgg_job {
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.bigdl.TrainVGG"
  local SPARK_CONF="--conf spark.executor.cores=1 --conf spark.cores.max=2 --conf spark.executorEnv.OMP_NUM_THREADS=1 --conf spark.executorEnv.KMP_BLOCKTIME=0 --conf OMP_WAIT_POLICY=passive --conf DL_ENGINE_TYPE=mklblas --conf spark.executor.memory=8G --driver-memory 4G"
  local ARGS="-f /tmp/cifar-10-batches-bin --lab $LABORATORY_MESOS_PATH --download /tmp -b 4"
  local SPARK_APP_JAR_URL="$LABORATORY_MESOS_PATH/$SPARK_APP_JAR"
  echo $NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS"
  local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"

  BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
  $NOEXEC update_json_field BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
  show_submission_message "$BIGDL_VGG_SPARK_DRIVER_SUBMIT_ID"
}

function main {

  parse_arguments "$@"

  if [ ! -f $config_file ]
  then
    error "$config_file not found.."
  else
    keyval $config_file
  fi

  # remove metadata file
  $NOEXEC rm -f "$APP_METADATA_FILE"

  header "Verifying required tools are installed...\n"

  require_dcos_cli

  require_spark

  require_jq

  header "Generating metadata for subsequent uninstalls...\n"

  generate_app_uninstall_metadata

  generate_deploy_conf
  deploy_app

  header "Running the BigDL VGG application... "
  run_bigdl_vgg_job
}

main "$@"
exit 0
