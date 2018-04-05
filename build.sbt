
import Dependencies._
import deployssh.DeploySSH._
import com.typesafe.sbt.packager.docker._
import NativePackagerHelper._

allowSnapshot in ThisBuild := true

scalaVersion in ThisBuild := Versions.Scala

val dockerRepositoryUrl = "fdp-reg.lightbend.com:443"

lazy val protobufs = (project in file("./protobufs"))
    .settings(
      PB.targets in Compile := Seq(
        PB.gens.java -> (sourceManaged in Compile).value,
        scalapb.gen(javaConversions=true) -> (sourceManaged in Compile).value
      ),
      publish := { }
    )

lazy val publisher = (project in file("./publisher"))
  .settings(
    name :="model-server-publisher",
    buildInfoPackage := "build",
    mainClass in Compile := Some("com.lightbend.kafka.DataProvider"),
    maintainer := "Fast Data Team <fdp@lightbend.com>",
    packageSummary := "Model Server Data Publisher",
    packageDescription := "Model Server Data Publisher",
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
    ),
    // mappings in Universal ++= directory("data"),
    dockerBaseImage := "openjdk:8u151-jre",
    dockerRepository := Some(dockerRepositoryUrl),
    //dockerCommands += Cmd("ADD", "data", "/opt/docker/data"),
    version in Docker := version.value.takeWhile(c => c != '+')

  )
  .settings(libraryDependencies ++= kafkaBaseDependencies ++ testDependencies)
  .dependsOn(protobufs, configuration)
  .enablePlugins(DeploySSH)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val model = (project in file("./model"))
  .settings(libraryDependencies ++= modelsDependencies,
    publish := { })
  .dependsOn(protobufs)
  .disablePlugins(DockerPlugin)

lazy val kafkaSvc = (project in file("./kafkastreamssvc"))
  .settings(
    buildInfoPackage := "build",
    name :="model-server-kstreams",
    mainClass in Compile := Some("com.lightbend.modelserver.withstore.ModelServerWithStore"),
    maintainer := "Boris Lublinsky <boris.lublinsky@lightbend.com",
    packageSummary := "Model Server Kafka Streams",
    packageDescription := "Model Server Kafka Streams",
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
    ),
    dockerBaseImage := "openjdk:8u151-jre",
    dockerRepository := Some(dockerRepositoryUrl),
    version in Docker := version.value.takeWhile(c => c != '+')

  )
  .settings(libraryDependencies ++= kafkaDependencies ++ webDependencies)
  .dependsOn(model, configuration)
  .enablePlugins(DeploySSH)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val akkaSvc = (project in file("./akkastreamssvc"))
  .settings(
    buildInfoPackage := "build",
    name :="model-server-akkastreams",
    mainClass in Compile := Some("com.lightbend.modelServer.modelServer.AkkaModelServer"),
    maintainer := "Fast Data Team <fdp@lightbend.com>",
    packageSummary := "Model Server Akka Streams",
    packageDescription := "Model Server Akka Streams",
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
    ),
    dockerBaseImage := "openjdk:8u151-jre",
    dockerRepository := Some(dockerRepositoryUrl),
    version in Docker := version.value.takeWhile(c => c != '+')
  )    .settings(libraryDependencies ++= kafkaDependencies ++ akkaServerDependencies
    ++ modelsDependencies ++ Seq(curator))
  .dependsOn(protobufs, configuration)
  .enablePlugins(DeploySSH)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val configuration = (project in file("./configuration"))
  .settings(libraryDependencies ++= Seq(typesafeConfig, influxDBClient, codecBase64),
    publish := { })
  .disablePlugins(DockerPlugin)

lazy val modelserver = (project in file("."))
  .settings(publish := { })
  .aggregate(protobufs, publisher, model, configuration, kafkaSvc, akkaSvc)

