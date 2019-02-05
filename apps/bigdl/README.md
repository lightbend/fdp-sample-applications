# Training a VGG network on CIFAR-10 Data

This application is currently only supported on DC/OS. It is being ported to OpenShift and Kubernetes.

> **DISCLAIMER:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

This sample application stress tests the DC/OS cluster by running a training and validation of a VGG network on a large dataset from [CIFAR-10](https://www.cs.toronto.edu/~kriz/cifar.html) image dataset. The application reports the loss for every epoch during training and validation. It uses [Intel's BigDL](https://github.com/intel-analytics/BigDL) library for deep learning on Spark.

> **NOTE:** This applications demonstrates using BigDL as a third-party library for machine learning. BigDL is not part of the Fast Data Platform distribution and Lightbend does not provide support for BigDL or applications that use it.

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

This will run the application for training the CIFAR-10 dataset on a VGG network on the local machine.

The `--master` argument is optional and is required only for the local run of the application.

## Deploying and Running on a DC/OS Cluster

The first step in deploying the applications on a DC/OS cluster is to prepare a docker image of the application. This can be done from within `sbt`.

### Prepare Docker Images

In the `bigdl/source/core/` directory, run `sbt`:

```
$ sbt
> projects
[info] 	 * bigdlSample
> docker
```

This will create a docker image named `lightbend/bigdlvgg:X.Y.Z` (for the current version `X.Y.Z`) with the default settings. The name of the Docker user comes from the `organization` field in `build.sbt` and should be changed there for your environment.

Once the docker image is created, you can push it to the repository at DockerHub.

## Deploying to Kubernetes and OpenShift

For Kubernetes and OpenShift the project provides `vggcifarchart` chart. (See [this](https://docs.bitnami.com/kubernetes/how-to/create-your-first-helm-chart/#values) for an introduction to Helm, if you need it.)

This chart has `chart.yaml` and `values.yaml` defining the content and values used in the chart.
It also has one deployment yaml file, `vggcifar.yaml`.

## Running a Custom Image on Kubernetes or OpenShift

Edit the `helm/values.yaml.template` and change the image locations or simply override the values when invoking `helm install ...`
