#!/bin/bash

## run directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
source $DIR/functions.sh

test_dcos_cli

JSON_TEMPLATE=$1
shift
JSON=${JSON_TEMPLATE%.template}
APP_NAME=$(cat $JSON_TEMPLATE | jq -r .id)
APP_STATUS=$(marathon_app_status $APP_NAME)

while test $# != 0
do
    case "$1" in
    --grafana) use_grafana=true ;;
    --influxdb) use_influxdb=true ;;
    esac
    shift
done

echo "Current App Deployment Status: $APP_STATUS"

if [[ "$APP_STATUS" = "DEPLOYED" ]];
then
  echo "Upgrading application $APP_NAME"
  dcos marathon app update $APP_NAME < $JSON
else
  echo "Deploying application $APP_NAME"

  cp "$JSON_TEMPLATE" "$JSON"
  update_kafka "$JSON"
  update_zk "$JSON"
  if [ "$use_grafana" = true ]; then
    update_host_port_config "/grafana" "GRAFANA" "$JSON"
  fi
  if [ "$use_influxdb" = true ]; then  
    update_host_port_config "/influxdb" "INFLUXDB" "$JSON"
  fi
  dcos marathon app add $JSON
fi  
