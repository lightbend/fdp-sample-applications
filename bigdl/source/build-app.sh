#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

# You could override these definitions with environment variables.
: ${S3_BUCKET:="fdp-sample-bigdl-vgg"}
: ${JAR:="bigdlsample-assembly-0.0.1.jar"}
: ${BIGDL_JAR:="bigdl-0.1.0-SNAPSHOT-jar-with-dependencies.jar"}
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
  cd "$DIR"
  echo "Building jar..."
  sbt clean clean-files

  # check if unmanaged dependency exists - the BigDL jar
  # if not then download the jar from S3

  if [ ! -d "$DIR/lib" ]; then
    echo "$DIR/lib" does not exist .. creating
    mkdir "$DIR/lib"
  fi

  if [ ! -f "$DIR/lib/bigdl-0.1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    echo BigDL jar does not exist .. Downloading from S3
    # It's actually staged in the region shown:
    aws s3 cp "s3://$S3_BUCKET/$BIGDL_JAR" "$DIR/lib" --region "ap-south-1"
  fi

  sbt assembly
  echo "Uploading jar $JAR to S3 bucket $S3_BUCKET..."
  aws s3 cp "$DIR/target/scala-2.11/$JAR" "s3://$S3_BUCKET/$JAR" --acl public-read --region "$AWS_DEFAULT_REGION"
}

function error {
  echo "ERROR: $@"
  echo ""
  exit 1
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
  check_required_utils
  check_required_vars

  echo "Using AWS_DEFAULT_REGION $AWS_DEFAULT_REGION and S3_BUCKET $S3_BUCKET for uploads"

  upload_app_jar
  echo "Artifacts built and pushed successfully!"
}

main "$@"
exit 0

