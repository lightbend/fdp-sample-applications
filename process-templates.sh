#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

echo $HERE

. $HERE/version.sh

for f in killrweather.yaml loaderinstall.yaml killrweather-app/src/main/resources/killrweatherAppDocker.json
do
  cat $HERE/$f.template | sed -e "s/VERSION/$VERSION/g" > $HERE/$f
done

