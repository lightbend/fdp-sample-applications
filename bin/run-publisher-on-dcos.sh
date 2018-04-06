#!/bin/bash
set -e
BROKER_VIP=`dcos kafka endpoints broker | jq -r .vip`
ZOOKEEPER=`dcos kafka endpoints zookeeper`
PUBLISHER_JSON_TEMPLATE="publisher.json.template"
PUBLISHER_JSON="publisher.json"
APP_NAME=`cat $PUBLISHER_JSON_TEMPLATE | jq -r .id`
APP_STATUS=`dcos marathon app show $APP_NAME`

[ "$APP_STATUS" = Error* ] && IS_APP_DEPLOYED=0 || IS_APP_DEPLOYED=1

echo "APP deployed: $IS_APP_DEPLOYED"

if [[ $IS_APP_DEPLOYED ]];
then
  echo "Upgrading application $APP_NAME"
  dcos marathon app update $APP_NAME < publisher.json
else
  echo "Deploying application $APP_NAME"

  cp "$PUBLISHER_JSON_TEMPLATE" "$PUBLISHER_JSON"
  sed -i -- "s~\bKAFKA_VIP\b~$BROKER_VIP~g" $PUBLISHER_JSON
  sed -i -- "s~\bZOOKEEPER\b~$ZOOKEEPER~g" $PUBLISHER_JSON

  dcos marathon app add publisher.json
fi  
