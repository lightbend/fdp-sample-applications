
import Dependencies._

version in ThisBuild := CommonSettings.version 
organization in ThisBuild := CommonSettings.organization
scalaVersion in ThisBuild := Versions.Scala

lazy val protobufs = (project in file("./protobufs"))
    .settings(
      PB.targets in Compile := Seq(
        PB.gens.java -> (sourceManaged in Compile).value,
        scalapb.gen(javaConversions=true) -> (sourceManaged in Compile).value
      ),
      publish := { }
    )

// Supporting projects used as dependencies
lazy val configuration = (project in file("./configuration"))
  .settings(libraryDependencies ++= Seq(typesafeConfig, influxDBClient, codecBase64))

lazy val model = (project in file("./model"))
  .settings(libraryDependencies ++= modelsDependencies)
  .dependsOn(protobufs)

// Publisher project - pure scala
lazy val publisher = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-akka-kafka-streams-model-server-model-publisher", stage, executableScriptName)("./publisher")
  .enablePlugins(JavaAppPackaging)
  .settings (
    mainClass in Compile := Some("com.lightbend.kafka.DataProvider"),
    libraryDependencies ++= kafkaBaseDependencies ++ testDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(protobufs, configuration)

// Both Kafka and Akka services are using Tensorflow
lazy val kafkaSvc = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-akka-kafka-streams-model-server-kafka-streams-server", stage, executableScriptName, dockerBaseImage = "tensorflow/tensorflow:latest-devel")("./kafkastreamssvc")
  .enablePlugins(JavaAppPackaging)
  .settings (
    mainClass in Compile := Some("com.lightbend.modelserver.withstore.ModelServerWithStore"),
    libraryDependencies ++= kafkaDependencies ++ webDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration)

lazy val akkaSvc = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-akka-kafka-streams-model-server-akka-streams-server", stage, executableScriptName, dockerBaseImage = "tensorflow/tensorflow:latest-devel")("./akkastreamssvc")
  .enablePlugins(JavaAppPackaging)
  .settings (
    mainClass in Compile := Some("com.lightbend.modelServer.modelServer.AkkaModelServer"),
    libraryDependencies ++= kafkaDependencies ++ akkaServerDependencies ++ Seq(curator),
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration)

lazy val modelserver = (project in file("."))
  .settings(publish := { })
  .aggregate(protobufs, publisher, model, configuration, kafkaSvc, akkaSvc)

