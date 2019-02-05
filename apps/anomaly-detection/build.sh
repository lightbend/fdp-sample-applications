#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

cd ${HERE}/source/core
sbt -no-colors "set version in ThisBuild := \"$VERSION\"" clean dockerBuildAndPush

echo "$PWD: built package and docker image(s). Pushed the docker image(s)."
