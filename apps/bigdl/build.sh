#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

cd ${HERE}/source/core
sbt "set version in ThisBuild := \"$VERSION\"" clean cleanFiles fdp-bigdl-vggcifar/clean fdp-bigdl-vggcifar/docker fdp-bigdl-vggcifar/dockerPush

echo "$PWD: built package and docker image(s). Pushed the docker image(s)."
