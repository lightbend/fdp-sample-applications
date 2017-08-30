# Training VGG network on cifar-10 data

This sample application stress tests the DC/OS cluster by running a training of a VGG network on a large dataset from cifar-10. It uses [Intel's BigDL](https://github.com/intel-analytics/BigDL) library for deep learning on Spark.

> **NOTE:** This applications demonstrates using BigDL as a third-party library for machine learning. BigDL is not part of the FDP distribution and Lightbend does not provide support for BigDL or applications that use it.

### Building the Model
The model uses BigDL from Intel as the base machine learning library. The build process downloads the BigDL jar and then prepares an assembly with the training module.

> **Note:** There is already a built assembly staged in S3 that you can use. Therefore, the only reason to build and stage the assembly yourself is when you customize it.

Go to folder `$PROJECT_HOME/bigdl/source` and issue the following command, where an environment variable is used to specify the S3 bucket name you want to use to upload your build assembly:

```
S3_BUCKET=my_s3_bucket ./build-app.sh
```

> **Note:** Also expose this S3 bucket as an HTTP end point; it will be needed when you run the application below.

The bucket should be in the location of your `EC2_AZ_REGION` definition in your `$HOME/.ssh/aws.sh` (used when you installed FDP). If the bucket is in a different region, then add this definition on the command line:

```
S3_BUCKET=my_s3_bucket AWS_DEFAULT_REGION=my_region ./build-app.sh
```

The `build-app.sh` script will download the BigDL jar, prepare an assembly and then upload the final assembly jar on to an S3 bucket to be used when we deploy the application on DC/OS.

### Deploying the Application

To run the application, first change to the directory `$PROJECT_HOME/bigdl/bin`.

If you want to run the version of this application already staged in S3, simply run this command:

```bash
./app-install.sh
```

If you want to run your custom build of the application, run this variant of the command, using the same `S3_BUCKET` value used when you built it:

```bash
S3_BUCKET=my_s3_bucket ./app-install.sh
```

The `app-install.sh` script will install the application as a DC/OS service. To see the progress of the model training performed by the job, check the logs (`stdout` and `stderr`) from the DC/OS console of the appropriate Spark driver.

Note the model training will run in epochs and will continue till the threshold of the error level will be reached. This will take a very very long time.

YOu can watch the driver logs using the command that is printed to the screen by `app-install.sh`:

```bash
dcos spark log driver-M-000N --follow
```

Where the `M` and `N` will be numbers for the job id.

If you want to kill the service in between, do the following from `$PROJECT_HOME/bigdl/bin`:

```bash
./app-remove.sh
```

The command that actually forks the model training can be found in `app-install.sh`. The current important parameters used are:

1. Spark driver memory - 4G
2. Executor Memory per executor - 8G
3. Batch size for training - 16
4. Number of nodes - 1
5. Core per node - 4

All of these can be changed but changing one may require changes to others. For example, changing batch size may require a change in the Java heap size or else an `OutOfMemoryError` may result.

### BigDL Analytics library

We use the [BigDL Analytics library](https://github.com/intel-analytics/BigDL) from Intel for the training. In fact the class `com.lightbend.fdp.sample.bigdl.TrainVGG` is based on a similar class in the library itself. The only change made here is that the downloading of the cifar-10 data is now done automatically as part of the running program. And it gets downloaded only if it has not been downloaded already.

The program can be used for checkpointing as well. Add the checkpointing option while invoking it as part of `app-install.sh`.

## A note about versioning

Don't put a `version := ...` setting in your sub-project because versioning is completely
controlled by [`sbt-dynver`](https://github.com/dwijnand/sbt-dynver) and enforced by the `Enforcer` plugin found in the `build-plugin`
directory.