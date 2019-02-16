#!/usr/bin/env bash

set -eu
: ${NOOP:=}

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

source $HERE/../../common.sh

docker_task="docker"
push_msg=
while [[ $# -gt 0 ]]
do
  case $1 in
     --push|--push-docker-images)
      docker_task="dockerBuildAndPush"
      push_msg="Pushed the docker images."
      ;;
    -v|--version*)
      shift
      VERSION=$(get_version $@)
      ;;
    *)
      error "Unrecognized argument $1"
      ;;
  esac
  shift
done

[[ -n $VERSION ]] || error "Version string can't be empty!"
info2 "Using version $VERSION"

cd ${HERE}/source/core
$NOOP sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show version" clean package $docker_task

echo "$PWD: built package and Docker images. $push_msg"
