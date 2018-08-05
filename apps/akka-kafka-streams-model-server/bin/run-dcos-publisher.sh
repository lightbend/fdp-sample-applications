#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
source $DIR/functions.sh

$DIR/run-dcos-svc.sh publisher.json.template
