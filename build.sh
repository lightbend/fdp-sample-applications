#!/usr/bin/env bash

set -eu

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$HERE/version.sh"

# The only allowed argument is the optional version string
[[ $# -gt 0 ]] && VERSION=$1
echo "$0: Using version $VERSION"

cd "$HERE"
sbt --no-colors "set version in ThisBuild := \"$VERSION\"" "show version" clean package docker
