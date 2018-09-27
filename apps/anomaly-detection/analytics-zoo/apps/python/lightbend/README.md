# Preparing the Docker image for Training

This document describes the steps needed to set up the docker image that will run the training of the model. It also discusses steps needed to do the following tasks:

1. Build `analytics-zoo` from source that forms the core of the BigDL environment needed to run the training
2. Edit / customize the Jupyter notebook for training
3. Convert the notebook to a Python script to add to the docker image
4. How to run the docker image locally


# Build analytics-zoo

```
$ pwd
.../fdp-sample-applications/apps/anomaly-detection/analytics-zoo
$ ./make-dist.sh
```

# Customize notebook for training (optional)

This is only applicable if you want to make any changes in the Jupyter notebook. It may be a good idea to play around with the neural network architecture for better familiarity or hope to have some more tuning of the hyperparameters.

## Setup environment variables:

```bash
$ export SPARK_HOME=<location of Spark 2.2>
$ export ZOO_HOME=<.../fdp-sample-applications/apps/anomaly-detection/analytics-zoo>
$ export MASTER=local[*]
$ export ANALYTICS_ZOO_HOME=$ZOO_HOME/dist
$ export ZOO_JAR=$ZOO_HOME/dist/lib/analytics-zoo-0.2.0-SNAPSHOT-jar-with-dependencies.jar
$ export ZOO_CONF=$ZOO_HOME/dist/conf/spark-analytics-zoo.conf
$ export ZOO_PY_ZIP=$ZOO_HOME/dist/lib/analytics-zoo-0.2.0-SNAPSHOT-spark-2.1.0-dist.zip

```

## Ensure the following are installed

1. Python3
2. Anaconda
3. Spark 2.2

## Patch spark-env.sh

Enter the following line in `$SPARK_HOME/conf/spark-env.sh`:
`PYSPARK_PYTHON=python3`

## Run Jupyter

```
$ cd analytics-zoo/apps/python/lightbend
$ .../analytics-zoo/scripts/jupyter-with-zoo.sh --master ${MASTER} \
                                                --driver-cores 2  \
                                                --driver-memory 8g  \
                                                --total-executor-cores 2  \
                                                --executor-cores 4  \
                                                --executor-memory 4g
```

## Open notebook

In the browser navigate to `http://localhost:8888` and open the notebook

## Convert notebook to Python script

`$ jupyter nbconvert --to script dnn_anomaly_bigdl.ipynb`

> Ensure `nbconvert` is installed. Or else install using `conda install nbconvert`

Once the notebook is converted to the Python script, we need to remove the `ipython` specific stuff from the generated python script. This is a manual process.

Here's an example ..

Remove / comment out the following line from the generated python script:

```
get_ipython().run_cell_magic('time', '', '# Boot training process\ntrained_model = optimizer.optimize()\nprint("Optimization Done.")')
```

and replace with the following:

```
trained_model = optimizer.optimize()
print("Optimization Done.")
```

Also remove all plot statements from the python script.

# Prepare docker image

```
$ pwd
$ZOO_HOME/apps/python/lightbend
$ docker build --rm -t lightbend/analytics-zoo:0.1.0-spark-2.2.0 .
```

# Run docker image

When running the docker image we need to mount the host folder containing `data/CPU_examples.csv` to the container folder `/opt/work/data`.

```
$ docker run -it --rm -v <data folder>:/opt/work/data lightbend/analytics-zoo:0.1.0-spark-2.2.0 bash
root@3d7a2d664c77:/opt/work# pwd
/opt/work
root@3d7a2d664c77:/opt/work# ls
analytics-zoo  analytics-zoo-0.1.0  analytics-zoo-SPARK_2.2-0.1.0-dist.zip  data  dnn_anomaly_bigdl.py  download-analytics-zoo.sh  get-pip.py  spark-2.2.0  start-notebook.sh
root@3d7a2d664c77:/opt/work# analytics-zoo/scripts/spark-submit-with-zoo.sh dnn_anomaly_bigdl.py
```

The above command will run the training of the model using the data files in `data` folder and generate a Tensorflow model in `/tmp/model.pb`. This can be used for scoring.







