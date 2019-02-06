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
for i in fdp-kstream-dsl fdp-kstream-processor
do
  $NOOP sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show version" $i/clean $i/$docker_task
done

echo "$PWD: built package and Docker images. $push_msg"
