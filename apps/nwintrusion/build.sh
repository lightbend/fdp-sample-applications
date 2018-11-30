#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

cd ${HERE}
bats test/bin/*.bats

echo "nwintrusion: VERSION = $VERSION"
cd ${HERE}/source/core
for i in fdp-nwintrusion-ingestion fdp-nwintrusion-anomaly fdp-nwintrusion-batchkmeans
do
  sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show version" $i/clean $i/docker $i/dockerPush
done

echo "$PWD: built package and docker image(s). Pushed the docker image(s)."
