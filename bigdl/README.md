# Training VGG network on cifar-10 data

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

This sample application stress tests the DC/OS cluster by running a training and validation of a VGG network on a large dataset from [CIFAR-10](https://www.cs.toronto.edu/~kriz/cifar.html) image dataset. The application reports the loss for every epoch during training and validation. It uses [Intel's BigDL](https://github.com/intel-analytics/BigDL) library for deep learning on Spark.

> **NOTE:** This applications demonstrates using BigDL as a third-party library for machine learning. BigDL is not part of the FDP distribution and Lightbend does not provide support for BigDL or applications that use it.

## Running the application Locally

The application can be run locally or on the DC/OS cluster.

`sbt` will be used to run applications on your local machine. The following examples demonstrate how to run the individual components from the `sbt` console.

```
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/bigdl/source/core/
[info] 	 * bigdlSample
> run --master local[4] -f /tmp/cifar-10-batches-bin --download /tmp -b 16
```

This will run the application for training the cifar-10 dataset on a VGG network on the local machine. 

The `--master` argument is optional and is required only for the local run of the application. 

## Deploying and running on DC/OS cluster

The first step in deploying the applications on DC/OS cluster is to prepare a docker image of the application. This can be done from within sbt.

### Prepare docker images

In the `bigdl/source/core/` directory:

```
$ sbt
> projects
[info] 	 * bigdlSample
> docker
```

This will create a docker image named `lightbend/bigdlvgg:X.Y.Z` (for the current version `X.Y.Z`) with the default settings. The name of the docker repository comes from the `organization` field in `build.sbt` and can be changed there for alternatives. If the repository name is changed, then the value of `$DOCKER_USERNAME` also needs to be changed in `bigdl/bin/app-install.sh`. The version of the image comes from `<PROJECT_HOME>/version.sh`. Change there if you wish to deploy a different version.

Once the docker image is created, you can push it to the repository at DockerHub.

### Installing on DC/OS cluster

The installation scripts are present in the `bigdl/bin` folder. The script that you need to run is `app-install.sh`.

```
$ pwd
.../bigdl/bin
$ ./app-install.sh --help

  Installs the BigDL sample app. Assumes DC/OS authentication was successful
  using the DC/OS CLI.

  Usage: app-install.sh   [options] 

  eg: ./app-install.sh 

  Options:

  -n | --no-exec              Do not actually run commands, just print them (for debugging).
  -h | --help                 Prints this message.
$ ./app-install.sh
```
**Here are a few points that you need to keep in mind before starting the applications on your cluster:**

1. Need to have done dcos authentication beforehand. Run `dcos auth login`.
2. Need to have the cluster attached. Run `dcos cluster attach <cluster name>`.
3. Need to have Spark running on the cluster.

### Removing the Application

If you want to remove the application, do the following from `$PROJECT_HOME/bigdl/bin`:

```bash
./app-remove.sh
```
