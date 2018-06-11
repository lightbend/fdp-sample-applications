#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

cd ${HERE}/source/core
for i in dslPackage procPackage
do
  sbt "set version in ThisBuild := \"$VERSION\"" "show version" $i/clean $i/docker
done

# Use this one to verify that the version is set correctly!
# sbt "set version in ThisBuild := \"$VERSION\"" "show version"
