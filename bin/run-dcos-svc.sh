#!/bin/bash

## run directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
source $DIR/functions.sh

JSON_TEMPLATE=$1
JSON=${JSON_TEMPLATE%.template}
APP_NAME=$(cat $JSON_TEMPLATE | jq -r .id)
APP_STATUS=$(marathon_app_status $APP_NAME)

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
  update_host_port_config "/grafana" "GRAFANA" "$JSON"
  update_host_port_config "/influxdb" "INFLUXDB" "$JSON"

  dcos marathon app add publisher.json
fi  
