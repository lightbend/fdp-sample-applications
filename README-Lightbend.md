# KillrWeather - Developers

These instructions are internal to Lightbend. They clarify the options described in `README-DEVELOPERS.md`, particular around having a web server to serve artifacts. Within Lightbend, the FDP team has a Docker image we deploy to FDP clusters for this purpose, called `fdp-laboratory-base`. The service in DC/OS is called `jim-lab`, after Jim Powers, who created this tool.

## Serving Artifacts

`README-DEVELOPERS.md` says that you need a web server to host and serve artifacts. Within Lightbend, we use `jim-lab`, a Docker image with an open port for `scp` uploading artifacts to a resident Netty instance. It  serves the artifacts from there. Here are the details that refine the `README-DEVELOPERS.md` instructions, where the section titles correspond to the titles there.

### Install a Web Server

Use `jim-lab`, https://github.com/lightbend/fdp-laboratory-base/, which supports the configuration options discussed in the other README.

See the instructions [Easy way to get started](https://github.com/lightbend/fdp-laboratory-base/blob/master/README.md#easy-way-to-get-started) in the project's [README](https://github.com/lightbend/fdp-laboratory-base/blob/master/README.md).

The Docker image is here, https://hub.docker.com/r/lightbend/fdp-laboratory-base/.

### Build and Deploy the Application Archives

We use the SBT plugin [sbt-deploy-ssh](https://github.com/shmishleniy/sbt-deploy-ssh) described in  `README-DEVELOPERS.md`.

#### Set Up deploy.conf

In what follows, more details of deploying to FDP-Lab and submitting the apps to Marathon are described [here](https://docs.google.com/document/d/1eMG8I4z6mQ0C4Llg1VHnpV7isnVAtnk-pOkDo8tIubI/edit#heading=h.izl4k6rmh4c0).

Copy `./deploy.conf.template` to `./deploy.conf` and edit the settings if necessary. However, they are already correct for `jim-lab`. (Use `jim-lab` if you deployed the default `fdp-laboratory-base` image.) Here is the default configuration:

```json
servers = [
  {
    name = "killrWeather"
    user = "publisher"
    host = jim-lab.marathon.mesos
    port = 9022
    sshKeyFile = "id_rsa"
  }
]
```

* `name`: the name used for identifying the configuration.
* `user`: the FDP user inside the container for ssh command.
* `host`: the Mesos name for the laboratory service.
* `port`: the ssh port (by default laboratory uses port 9022).
* `sshKeyFile`: the file used by the laboratory configuration to authenticate the user (should not have a password).

#### Deploy the Application

We can use the `sbt deploySsh ...` command, because `jim-lab` supports it:

```bash
sbt 'deploySsh killrWeather'
```

This will create the jars and copy them to the `jim-lab` image, directory `/var/www/html`. 
If you want to run ingesters from `jim-lab` you will also have to manually copy up the contents of the `data` directory there using
````
scp -P 9022 "local location" "remote location"
````
Note - if you only want to build artifacts locally without pushing them to the web server just run
```bash
sbt deploySsh
```
which will create all of the artifacts locally without pushing them to cluster. 

#### Run the Main Application with Marathon

You should only need to set the correct `VERSION` string in `KillrWeather-app/src/main/resource/KillrweatherApp.json`. Then deploy using the DC/OS CLI command described in the other README.

```bash
dcos marathon app add < killrweather-app/src/main/resource/killrweatherApp.json
```

You can also use the _Structured Streaming_ alternative, as described in the other README.

### Deploy the Clients

Similarly, you should only need to set the correct `VERSION` string in `./killrweather-httpclient/src/main/resources/killrweatherHTTPClient.json` and `./killrweather-grpcclient/src/main/resources/killrweatherGRPCClient.json`.

