# Training VGG network on cifar-10 data

This sample application stress tests the DC/OS cluster by running a training and validation of a VGG network on a large dataset from [CIFAR-10](https://www.cs.toronto.edu/~kriz/cifar.html) image dataset. The application reports the loss for every epoch during training and validation. It uses [Intel's BigDL](https://github.com/intel-analytics/BigDL) library for deep learning on Spark.

> **NOTE:** This applications demonstrates using BigDL as a third-party library for machine learning. BigDL is not part of the FDP distribution and Lightbend does not provide support for BigDL or applications that use it.

### Installing the Application

The easiest way to install the Network Intrusion Detection application is to install it from the pre-built docker image that comes with the Fast Data Platform distribution. Start from `fdp-package-sample-apps/README.md` of the distribution for general instructions on how to deploy the image as a Marathon application.

Once you have installed the docker image (we call it the laboratory) with the default name `fdp-apps-lab`, you can follow the steps outlined in that document to complete the installation of the application. The following part of this document discusses the installation part in more details.

> Assumption: We have `fdp-apps-lab` running in the FDP DC/OS cluster

```bash
$ pwd
<home directory>/fdp-package-sample-apps
$ cd bin/bigdl
$ ./app-install.sh
```

The default invocation of the script will install and run all the services of this application.

Try the `--help` option for `app-install.sh` for the command-line options.

The script `app-install.sh` takes all configuration parameters from a properties file.  The default file is `app-install.properties` which resides in the same directory, but you can specify the file with the `--config-file` argument.  It is recommended that you keep a set of configuration files for personal development, testing, and production.  Simply copy the default file over and modify as needed.

```
## laboratory mesos deployment
laboratory-mesos-path=http://fdp-apps-lab.marathon.mesos

## Spark package version - Must use Spark 2.1.1 for BigDL
spark-package-version=1.1.0-2.1.1-hadoop-2.7
```

> The installation process fetches the data required from the canned docker image in `fdp-apps-lab`.

Once the installation is complete, the Spark driver should be seen available on the DC/OS console.

> *Besides installing from the supplied docker image, the distribution also publishes the development environment and the associated installation scripts in `fdp-sample-apps/bigdl/bin` folder. The prerequisite of using these scripts is to have a developer version of the laboratory available as part of your cluster. This will be available in a future version of the platform.*

## Running the application

Once the required Spark drivers start running, the training process will start in epochs. The STDOUT of DC/OS console displays the progression of the training along with the error rate in classification.

### Removing the Application

If you want to remove the application, do the following from `$PROJECT_HOME/bigdl/bin`:

```bash
./app-remove.sh
```
