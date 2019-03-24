# Training a VGG network on CIFAR-10 Data

This application is currently only supported on DC/OS. It is being ported to OpenShift and Kubernetes.

> **DISCLAIMER:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

This sample application stress tests the cluster by running a training and validation of a VGG network on a large dataset from [CIFAR-10](https://www.cs.toronto.edu/~kriz/cifar.html) image dataset. The application reports the loss for every epoch during training and validation. It uses [Intel's BigDL](https://github.com/intel-analytics/BigDL) library for deep learning on Spark.

> **NOTE:** This applications demonstrates using BigDL as a third-party library for machine learning. BigDL is not part of the Fast Data Platform distribution and Lightbend does not provide support for BigDL or applications that use it.

## Running the Application Locally

The application can be run locally or on a cluster.

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

## Deploying and Running on a Cluster

The first step in deploying the applications on a cluster is to prepare a docker image of the application. This can be done from within `sbt`.

### Prepare Docker Images

Building the app can be done using the convenient `build.sh` or `sbt`.

For `build.sh`, use one of the following commands:

```bash
build.sh
build.sh --push-docker-images
```

Both effectively run `sbt clean compile docker`, while the second variant also pushes the images to your Docker Hub account. _Only use this option_ if you first change `organization in ThisBuild := CommonSettings.organization` to `organization in ThisBuild := "myorg"` in `source/core/build.sbt`!

In the `bigdl/source/core/` directory, run `sbt`:

```
$ sbt
> projects
[info] 	 * bigdlSample
> docker
```

This will create a docker image named `lightbend/bigdlvgg:X.Y.Z` (for the current version `X.Y.Z`) with the default settings. You can use the `sbt` target `dockerPush` to push the images to Docker Hub, but only after changing the `organization` as just described. You can publish to your local (machine) repo with the `docker:publishLocal` target.

For IDE users, just import a project and use IDE commands.

## Deploying to Kubernetes and OpenShift

For Kubernetes and OpenShift the project provides `vggcifarchart` chart. (See [this](https://docs.bitnami.com/kubernetes/how-to/create-your-first-helm-chart/#values) for an introduction to Helm, if you need it.)

This chart has `chart.yaml` and `values.yaml` defining the content and values used in the chart.
It also has one deployment yaml file, `vggcifar.yaml`.

## Running a Custom Image on Kubernetes or OpenShift

Edit the `helm/values.yaml.template` and change the image locations or simply override the values when invoking `helm install ...`
