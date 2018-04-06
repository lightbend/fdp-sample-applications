function req_marathon_app {
  APP_NAME=$1
  APP_DESC=`dcos marathon app show $APP_NAME`
  if [[ ! $? -eq 0 ]];
  then  
    echo "Required dependency $APP_NAME is not available - please deploy first"
    exit 1
  fi
  echo "$APP_DESC"
}

# Checks whether the given application exists in Marathon
function marathon_app_status {
  APP_NAME=$1
  RES=`dcos marathon app show $APP_NAME` && APP_STATUS="DEPLOYED" || APP_STATUS="NOT_FOUND"
  echo "$APP_STATUS"
}

function update_host_port_config {
  DEP="$1"
  NAME="$2"
  TARGET_FILE="$3"
  CONFIG_JSON=$(req_marathon_app "$DEP")
  HOST=$(echo "$CONFIG_JSON" | jq -r .tasks[0].host)
  PORT=$(echo "$CONFIG_JSON" | jq -r .tasks[0].ports[0])
  HOST_PLACEHOLDER="${NAME}_HOST_PLACEHOLDER"
  PORT_PLACEHOLDER="${NAME}_PORT_PLACEHOLDER"

  echo "For dep $DEP and placeholder $PLACEHOLDER with target $TARGET_FILE"
  echo "$HOST_PLACEHOLDER = $HOST"
  echo "$PORT_PLACEHOLDER = $PORT"

  sed -i -- "s~\b${HOST_PLACEHOLDER}\b~$HOST~g" $TARGET_FILE
  sed -i -- "s~\b${PORT_PLACEHOLDER}\b~$PORT~g" $TARGET_FILE
}

function update_kafka {
  TARGET_FILE=$1
  BROKER_VIP=`dcos kafka endpoints broker | jq -r .vip`
  sed -i -- "s~\bKAFKA_BROKERS_PLACEHOLDER\b~$BROKER_VIP~g" $TARGET_FILE
}

function update_zk {
  TARGET_FILE=$1
  ZOOKEEPER=`dcos kafka endpoints zookeeper`
  sed -i -- "s~\bZOOKEEPER_URL_PLACEHOLDER\b~$ZOOKEEPER~g" $TARGET_FILE
}

function test_dcos_cli {
  RES=`dcos`
  if [[ ! $? -eq 0 ]];
  then  
    echo "Required DCOS CLI is not available - please install it first"
    exit 1
  fi
}