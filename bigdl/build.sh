#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

# cd ${HERE}
# bats test/bin/*.bats

cd ${HERE}/source/core
sbt "set version in ThisBuild := \"$VERSION\"" clean cleanFiles fdp-bigdl-vggcifar/clean fdp-bigdl-vggcifar/docker fdp-bigdl-vggcifar/dockerPush

# Use this one to verify that the version is set correctly!
# sbt "set version in ThisBuild := \"$VERSION\"" "show version"
