#!/usr/bin/env bash
set -e
# builds all artifacts and pushes them to docker registry and s3

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

# You could override these definitions with environment variables.
: ${S3_BUCKET:="fdp-kdd-network-intrusion"}
: ${JAR:="fdp-nw-intrusion-assembly-0.1.jar"}
: ${AWS_ENV_FILE:=$HOME/.ssh/aws.sh}

function install_aws_cli {
  curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
  unzip awscli-bundle.zip
  sudo ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws
}

function install_python {
  case $(uname) in
    Linux*)
    sudo apt-get install python
    ;;
    Darwin*)
    brew install python
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install unzip."
  exit 1
  ;;
  esac
}

function install_sbt {
  case $(uname) in
    Linux*)
    echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
    sudo apt-get update
    sudo apt-get install sbt
    ;;
    Darwin*)
    brew install sbt
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install sbt."
  exit 1
  ;;
  esac
}

function install_unzip {
  case $(uname) in
    Linux*)
    sudo apt-get install unzip
    ;;
    Darwin*)
    brew install unzip
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install unzip."
  exit 1
  ;;
  esac
}

function install_curl {
  case $(uname) in
    Linux*)
    sudo apt-get install curl
    ;;
    Darwin*)
    brew install curl
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install curl."
  exit 1
  ;;
  esac
}

function upload_app_jar {
  CDIR="$(pwd)"
  cd "$DIR/core"
  echo "Building jar..."
  sbt clean clean-files
  sbt assembly
  cd "$CDIR"
  echo "Uploading jar $JAR to S3 bucket $S3_BUCKET..."
  aws s3 cp "$DIR/core/target/scala-2.11/$JAR" "s3://$S3_BUCKET/$JAR" --acl public-read
}

function build_and_push_images {
  declare ARGS="--docker-username $DOCKER_USERNAME --docker-password $DOCKER_PASSWORD"

  # build and push data load image
  # $DIR/docker/load-intrusion-data/build-image.sh $ARGS

  # build data and push transformation image
  # $DIR/docker/transform-intrusion-data/build-image.sh $ARGS

  # build data and push the visualization image
  $DIR/docker/visualize-intrusion-data/build-image.sh $ARGS
}

function show_help {
cat<< EOF
  Builds and pushes all artifacts of this app to Docker Hub.
  Usage: $SCRIPT  [Options]

  eg: ./$SCRIPT --option-file /path/to/options_file

  Options:
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
  -h, --help                      prints this message.

The Docker user name and password are required. They can be specified one of three ways:
1. Define the environment variables DOCKER_USERNAME and DOCKER_PASSWORD in advance.
2. Specify both the --docker-username and --docker-password options.
3. Specify the --option-file option.
EOF
}

function error {
  echo "ERROR: $@"
  echo ""
  show_help
  exit 1
}

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

  # upload_app_jar
  build_and_push_images
  echo "Artifacts built and pushed sucessfully!"
}

main "$@"
exit 0
