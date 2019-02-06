#!/usr/bin/env bash

set -eu
: ${NOOP:=}

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

docker_task="docker"
push_msg=
while [[ $# -gt 0 ]]
do
  case $1 in
     --push|--push-docker-images)
      docker_task="dockerBuildAndPush"
      push_msg="Pushed the docker images."
      ;;
    *) ;;
  esac
  shift
done

cd ${HERE}/source/core
$NOOP sbt -no-colors "set version in ThisBuild := \"$VERSION\"" clean cleanFiles fdp-bigdl-vggcifar/clean fdp-bigdl-vggcifar/$docker_task

echo "$PWD: built package and Docker images. $push_msg"
