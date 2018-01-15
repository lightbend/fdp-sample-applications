#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

# cd ${HERE}
# bats test/bin/*.bats

cd ${HERE}/source/core
sbt clean test package
