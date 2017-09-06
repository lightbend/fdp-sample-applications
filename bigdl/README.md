# Training VGG network on cifar-10 data

This sample application stress tests the DC/OS cluster by running a training of a VGG network on a large dataset from cifar-10. It uses [Intel's BigDL](https://github.com/intel-analytics/BigDL) library for deep learning on Spark.

> **NOTE:** This applications demonstrates using BigDL as a third-party library for machine learning. BigDL is not part of the FDP distribution and Lightbend does not provide support for BigDL or applications that use it.

### Installing the Application

The application installation process consists of the following steps:

1. Build the application
2. Deploy the application package in to the laboratory
3. Install the application as a Spark driver

The only script you need to run is the installation script which does all the steps transparently for you.

```bash
$ cd bin
$ ./app-install.sh
```

As usual you can see the various options using `--help` above. The installation script needs a property file, which defaults to `app-install.properties` having the following structure:

```
## name of the user used to publish the artifact.  Typically 'publisher'
publish-user="publisher"

## the IP address of the publish machine (where laboratory is running)
publish-host=jim-lab.marathon.mesos

## port for the SSH connection. The default configuration is 9022
ssh-port=9022

## passphrase for your SSH key.  Remove this entry if you don't need a passphrase
passphrase=

## the key file in ~/.ssh/ that is to be used to connect to the deployment host
ssh-keyfile="dg-test-fdp.pem"

## laboratory mesos deployment
laboratory-mesos-path=http://jim-lab.marathon.mesos
```

### Removing the Application

If you want to remove the application, do the following from `$PROJECT_HOME/bigdl/bin`:

```bash
./app-remove.sh
```
