# Flink Sample Application

The sample application is adapted from the publicly-available [Flink training](http://dataartisans.github.io/flink-training/) from [dataArtisans](http://data-artisans.com/). It uses a public dataset of taxi rides in New York City. The details of the dataset can be found [here](http://dataartisans.github.io/flink-training/exercises/taxiData.html). In summary, the application does the following:

1. Load the dataset through a Marathon application
2. Read the dataset from a Kafka topic (`taxiin`)
3. Analyze and transform the dataset
4. Train a regression classifier for travel time prediction
5. Predict travel time based on model in Step 3
6. Write the prediction to another Kafka topic (`taxiout`)

The main components of running the Flink sample application are:

1. Deploy the data loading Marathon app which will pull data from an S3 bucket and load it into a Kafka topic (`taxiin`)
2. Run the sample application following the steps outlined below.

> **Notes:**
>
> 1. The DC/OS CLI plugin for Flink is not yet available. Hence, we need to run the application manually.
> 2. While Flink is bundled with FDP, it is considered experimental at this time. The 1.0 release may not include production support for Flink.


## Deploy the Data Loading Application
1. `$ git clone https://github.com/typesafehub/fdp-sample-apps.git`
2. `$ cd fdp-sample-apps`
3. `$ cd flink/source`
4. `$ ./build-app.sh --docker-username <docker-user-name> --docker-password <docker-password>`. **This needs to be done only once if you want to build the application and upload the data and the application jar to a docker repository and S3**. Try `.build-app.sh --help` for more options.
5. Change directory to `fdp-sample-apps/flink/bin`
6. `$ ./app-install.sh`. This reads from a properties file `app-install.properties`. Please edit the settings in this properties file before running. You can also supply your own properties file. (Try `./app-install.sh --help` for details).

## Run the Sample Application

At this time, the DC/OS CLI for Flink is not yet released. Therefore, we will need to `ssh` into the appropriate cluster node to run the Flink app. (This tedious process will no longer be necessary once the DC/OS CLI is available.)

We'll use some convenience tools that come with `fdp-installer`, so begin by defining an environment variable that points to the root directory of that package. Call it `FDP_INSTALLER_HOME`. We'll use scripts in `$FDP_INSTALLER_HOME/bin`. So, for example, if it's in `$HOME/fdp-installer`, use the following in a terminal window:

```bash
export FDP_INSTALLER_HOME=$HOME/fdp-installer
```

1. Go to the Flink UI in your FDP cluster:
  1. Open the DC/OS UI.
  2. Open the _Services_ view.
  3. Click the link for `flink`.
  4. On the Flink page. click the _Open Service_ button, which opens a new tab with the Flink UI.
2. In the Flink UI, click the _JobManager_ page.
3. Find the `jobmanger.rpc.address` and the `jobmanager.rpc.port`. The address must be exact due to Akka remoting requirements. This address will be of the form `ip-10-10-x-xxx`.

With that address, run the following convenience script to check if an entry already exists in the master's `/etc/hosts`. Run the following in the root `flink` directory for this project:

```bash
bin/check-job-manager-ip.sh jobmanager_rpc_address
```

If you have more than one master, select one to use for the rest of these steps. There is a `--help` option that explains optional arguments.

It will show the entry, if found, and report success. If the entry wasn't found, it will tell you what command to run so you can edit the file to add the entry. If you have to do that, follow these steps:

1. Run the command `$FDP_INSTALLER_HOME/bin/fdp-ssh.sh --master` to log into the master.
2. Edit the file: `sudo vi /etc/hosts`.
3. Add an entry for the Job Manager RPC address, replacing the 'x' values appropriately:
```text
10.10.x.xxx ip-10-10-x-xxx
```

Next you'll need to install the Flink application on the same master node, where we'll run it.
Run the following script to copy the assembly jar in `source/core/target/scala-2.11` up to the master. As before, run this script from the root `flink` directory:

```bash
bin/copy-app-to-master.sh
```

There is a `--help` option that explains optional arguments.

Now you need to install a Flink distribution on the same master node. You'll want a Scala 2.11 build for a recent Hadoop release. Here is a direct link to a suitable release: http://mirrors.ibiblio.org/apache/flink/flink-1.2.0/flink-1.2.0-bin-hadoop27-scala_2.11.tgz.

To install it, use the following steps:

1. Log into the master:
```bash
$FDP_INSTALLER_HOME/bin/fdp-ssh.sh --master
```
2. Run this command, which downloads the file to `flink.tgz`:
```bash
curl -o flink.tgz http://mirrors.ibiblio.org/apache/flink/flink-1.2.0/flink-1.2.0-bin-hadoop27-scala_2.11.tgz
```
3. Expand the archive:
```bash
gunzip flink.tgz
tar xf flink.tar
```

You'll now have the folder `flink-1.2.0` with the distribution.

Next you need one or more addresses for the Kafka brokers. From a terminal on your workstation:

1. Run the command `dcos kafka connection`.
2. Pick one of the values in the `dns` section. Use that `kafka_broker` in what follows.
3. Recall the Flink Job Manager address and port values we discovered previously.

Finally, we are ready to run the application! Back on the master node, run this command, where I've set it up as a script that defines variables for all the addresses, etc. Change the values to match your configuration!

```bash
cd $HOME   # start from the home directory on the master.
kafka_broker="broker-0.kafka.mesos:9875"
job_manager_address=ip-10-10-1-115
job_manager_port=21245

flink-1.2.0/bin/flink run -m $job_manager_address:$job_manager_port \
  flink-app/fdp-flink-taxiride-assembly-0.1.jar \
  --broker-list $kafka_broker --inTopic taxiin --outTopic taxiout
```

This will continue running indefinitely. You might wish to run it in the background by appending a `&` to the end of the last command.

Note that you need to have the 2 topics `taxiin` and `taxiout` created on Kafka in the DC/OS cluster, which we did earlier. You can track the output by reading from the out topic `taxiout` using any Kafka consumer client app for this purpose.

In the Flink UI accessible from the DC/OS UI, you should see the running job shown under the _Running Jobs_ tab.

## A note about versioning

Don't put a `version := ...` setting in your sub-project because versioning is completely
controlled by [`sbt-dynver`](https://github.com/dwijnand/sbt-dynver) and enforced by the `Enforcer` plugin found in the `build-plugin`
directory.