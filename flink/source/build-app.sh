#!/usr/bin/env bash
set -e
# builds all artifacts and pushes them to docker registry and s3

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

# You could override these definitions with environment variables.
: ${S3_BUCKET:="fdp-sample-flink-taxirides"}
: ${JAR:="fdp-flink-taxiride-assembly-0.1.jar"}
: ${AWS_ENV_FILE:=$HOME/.ssh/aws.sh}

function upload_app_jar {
  CDIR="$(pwd)"
  cd "$DIR/core"
  echo "Building jar..."
  $NOEXEC sbt clean clean-files
  $NOEXEC sbt assembly
  cd "$CDIR"
  echo "Uploading jar $JAR to S3 bucket $S3_BUCKET..."
  $NOEXEC aws s3 cp "$DIR/core/target/scala-2.11/$JAR" "s3://$S3_BUCKET/$JAR" --acl public-read
}

function build_and_push_images {
  declare ARGS="--docker-username $DOCKER_USERNAME --docker-password $DOCKER_PASSWORD"

  # build and push data load image
  $NOEXEC $DIR/docker/load-taxi-data/build-image.sh $ARGS
}


# Used by show_help
HELP_MESSAGE="Builds and pushes all artifacts of this app to Docker Hub."
HELP_EXAMPLE_OPTIONS="--docker-username <user> --docker-password <password>"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  --du | --docker-username name   Username on Docker Hub. Part of the repo format.
                                  <hub-user>/<repo-name>:<tag>
  --dp | --docker-password pwd    Password for the account on Docker Hub.
  --s3 | --s3-bucket              S3 bucket to store artifacts.
                                  Default: fdp-kdd-network-intrusion.
  @file | -o | --option-file file Source "file" to define any of the user name,
                                  password, and/or the S3 bucket instead of using
                                  the command line options. This is more secure,
                                  IF "file" has locked down read permissions!
                                  The format must be the following:
                                    DOCKER_USERNAME=...
                                    DOCKER_PASSWORD=...
                                    S3_BUCKET=...

The Docker user name and password are required. They can be specified one of three ways:
1. Define the environment variables DOCKER_USERNAME and DOCKER_PASSWORD in advance.
2. Specify both the --docker-username and --docker-password options.
3. Specify the --option-file option.
EOF
)

function parse_arguments {
  while [ $# -gt 0 ]; do
    case "$1" in
      --du|--docker-username)
      if [ -n "$2" ]; then
        shift
        DOCKER_USERNAME=$1
      else
        error '"--docker-username" requires a user name argument.'
      fi
      ;;
      --dp|--docker-password)
      if [ -n "$2" ]; then
        shift
        DOCKER_PASSWORD=$1
      else
        error '"--docker-password" requires a password argument.'
      fi
      ;;
      @*)
      file=${1#@}
      if [ -n "$file" ]; then
        if [ -f "$file" ]; then
          source $file
        else
          error "file $file does not exist!"
        fi
      else
        error '"@file" required, but "@" found!'
      fi
      ;;
      -o|--option-file)
      if [ -n "$2" ]; then
        shift
        file="$1"
        if [ -f "$file" ]; then
          source $file
        else
          error "file $file does not exist!"
        fi
      else
        error '"--option-file" requires a file argument.'
      fi
      ;;
      --s3|--s3-bucket)
      if [ -n "$2" ]; then
        shift
        S3_BUCKET=$1
      else
        error '"--s3-bucket" requires a non-empty option argument.'
      fi
      ;;
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
      --)          # End of all options.
      shift
      break
      ;;
      '')          # End of all options.
      break
      ;;
      *)
      error "The option is not valid: $1"
      ;;
    esac
    shift
  done
}

function check_required_utils {
  type python >/dev/null 2>&1 || {
    echo >&2 "python is required but it's not installed. Installing python...";
    install_python
  }
  echo "$(type python)"

  type sbt >/dev/null 2>&1 || {
    echo >&2 "sbt is required but it's not installed. Installing sbt...";
    install_sbt
  }
  echo "$(type sbt)"

  type unzip >/dev/null 2>&1 || {
    echo >&2 "unzip is required but it's not installed. Installing unzip...";
    install_unzip
  }
  echo "$(type unzip)"

  type curl >/dev/null 2>&1 || {
    echo >&2 "curl is required but it's not installed. Installing curl...";
    install_curl
  }
  echo "$(type curl)"

  type aws >/dev/null 2>&1 || {
    echo >&2 "aws is required but it's not installed. Installing aws...";
    install_aws_cli
  }
  echo "$(type aws)"
}

function print_env_error {
  echo  "$1 not defined: $AWS_ENV_FILE either doesn't exist or it doesn't define $1."
  error "See https://developer.lightbend.com/docs/fast-data-platform/0.1.0/installation/index.html for more information."
}

function check_required_vars {
  if [ -z $AWS_ACCESS_KEY_ID ]; then
    echo "AWS_ACCESS_KEY_ID not defined: Trying $AWS_ENV_FILE"
    [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
    if [ -z $AWS_ACCESS_KEY_ID ]; then
      print_env_error "AWS_ACCESS_KEY_ID"
    fi
  fi

  if [ -z $AWS_SECRET_ACCESS_KEY ]; then
    echo "AWS_SECRET_ACCESS_KEY not defined: Trying $AWS_ENV_FILE"
    [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
    if [ -z $AWS_SECRET_ACCESS_KEY ]; then
      print_env_error "AWS_SECRET_ACCESS_KEY"
    fi
  fi

  if [ -z $AWS_DEFAULT_REGION ]; then
    echo "AWS_DEFAULT_REGION not defined: Trying to acquire AWS_DEFAULT_REGION from EC2_AZ_REGION in $AWS_ENV_FILE"
    [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
    if [ -z $EC2_AZ_REGION ]; then
      error "EC2_AZ_REGION is not defined in $AWS_ENV_FILE. Please define AWS_DEFAULT_REGION to proceed."
    fi
    export AWS_DEFAULT_REGION="${EC2_AZ_REGION%?}"
  fi

  export AWS_DEFAULT_OUTPUT="json"
  # source is not enough for the aws cli to pick up the vars
  export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
  export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY

  echo "Variables to use for the configuration of the aws cli command util..."
  echo "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY defined"
  echo "AWS_DEFAULT_REGION = $AWS_DEFAULT_REGION"
  echo "AWS_DEFAULT_OUTPUT = $AWS_DEFAULT_OUTPUT"
}

function main {
  parse_arguments "$@"
  check_required_utils
  check_required_vars

  if [ -z "$DOCKER_USERNAME" ]; then
    error "A docker username must be provided..."
  fi

  if [ -z "$DOCKER_PASSWORD" ]; then
    error "A docker password must be provided..."
  fi

  upload_app_jar
  build_and_push_images
  echo "Artifacts built and pushed sucessfully!"
}

main "$@"
exit 0

