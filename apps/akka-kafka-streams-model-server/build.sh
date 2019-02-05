#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

docker_task="docker"
case $1 in
   --push|--push-docker-images)
    docker_task="dockerBuildAndPush"
    push_msg="Pushed the docker images."
    ;;
  *) ;;
esac

cd ${HERE}/source/core
$NOOP sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show version" clean package $docker_task

echo "$PWD: built package and Docker images. $push_msg"
