#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

echo $HERE

. $HERE/version.sh

cd $HERE
sbt "set version in ThisBuild := \"$VERSION\"" "show version" clean package dockerBuildCommand

