#!/usr/bin/env bash

set -eu

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

docker_task="docker"
case $1 in
   --push|--push-docker-images)
    docker_task="dockerBuildAndPush"
    push_msg="Pushed the docker images."
    ;;
  *) ;;
esac

cd ${HERE}
bats test/bin/*.bats

cd ${HERE}/source/core
for i in fdp-flink-ingestion fdp-flink-taxiride
do
  $NOOP sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show version" $i/clean $i/$docker_task
done

echo "$PWD: built package and Docker images. $push_msg"
