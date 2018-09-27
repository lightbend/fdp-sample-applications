#!/bin/bash

function make_hyperparams_file {

  local learning_rate="$1"
  local training_epochs="$2"
  local batch_size="$3"

  cat <<EOM >$HYPERPARAMS_FILE_NAME
[HyperparameterSection]
learning_rate = $learning_rate
training_epochs = $training_epochs
batch_size = $batch_size
EOM
}

while true
do

  filename=$GENERATION_COMPLETE_FILE_NAME
  while [ ! -f $filename ]
  do
    sleep 2
  done
  echo "Data generation completed .."
  cat $filename

  echo Hyperparams file : $HYPERPARAMS_FILE_NAME

  declare -a learning_rate=('0.001' '0.005' '0.001')
  declare -a training_epochs=('8' '8' '8')
  declare -a batch_size=('256' '128' '64')

  # number of hyperparam set is the length of this array
  hyperparam_count=${#learning_rate[@]}

  counter=0
  while [  $counter -lt $hyperparam_count ]; do
    make_hyperparams_file ${learning_rate[$counter]} ${training_epochs[$counter]} ${batch_size[$counter]} 
    cat $HYPERPARAMS_FILE_NAME

    echo "Starting training .."
    analytics-zoo/scripts/spark-submit-with-zoo.sh dnn_anomaly_bigdl.py
    echo "Training done"
  
    # remove the generation complete file
    rm $GENERATION_COMPLETE_FILE_NAME
  
    datafile=$(ls -l $DATA_FILE_NAME)
    echo $datafile
  
    pbfile=$(ls -l $MODEL_PB_FILE_NAME)
    echo $pbfile
  
    attribfile=$(ls -l $MODEL_ATTRIB_FILE_NAME)
    echo $attribfile
    cat $MODEL_ATTRIB_FILE_NAME
  
    echo date > $TRAINING_COMPLETE_FILE_NAME
    echo "Training & model generation complete for hyperparams ${learning_rate[$counter]} ${training_epochs[$counter]} ${batch_size[$counter]}" 
    cat $FILE

    # allow the publisher module to publish this model to Kafka
    sleep 10

    let counter=counter+1 
  done

done
