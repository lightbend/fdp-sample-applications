name := "AnomalyDetection"

import Dependencies._

scalaVersion in ThisBuild := Versions.Scala
version in ThisBuild := CommonSettings.version
organization in ThisBuild := CommonSettings.organization

lazy val protobufs = (project in file("./protobufs"))
  .settings(Common.settings: _*)
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

// Supporting projects used as dependencies
lazy val configuration = (project in file("./configuration"))
  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Seq(cats) ++ configDependencies)

// Supporting projects used as dependencies
lazy val influxSupport = (project in file("./influxsupport"))
  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Seq(influxDBClient, scalaHTTP, cats, catsEffect, guava))
  .dependsOn(protobufs, configuration)

// Supporting projects used as dependencies
lazy val kafkaSupport = (project in file("./kafkasupport"))
  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= kafkaBaseDependencies)

// Supporting projects used as dependencies
lazy val model = (project in file("./model"))
  .settings(libraryDependencies ++= Dependencies.modelsDependencies)
  .dependsOn(protobufs)


// Publisher project - pure scala
lazy val adpublisher = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-ad-data-publisher", stage, executableScriptName)("./adpublisher")
  .enablePlugins(JavaAppPackaging)
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.publish.DataProvider"),
    libraryDependencies ++= kafkaBaseDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(protobufs, configuration, influxSupport, kafkaSupport)

// Ingestion project - pure scala
lazy val adTrainingDataIngestion = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-ad-training-data-ingestion", stage, executableScriptName)("./adTrainingDataIngestion")
  .enablePlugins(JavaAppPackaging)
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.training.ingestion.TrainingIngestion"),
    libraryDependencies ++= Seq(influxDBClient, cats, monix) ++ configDependencies
  )
  .dependsOn(configuration, influxSupport, kafkaSupport)

// Model publishing project - pure scala
lazy val adTrainingModelPublish = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-ad-training-model-publish", stage, executableScriptName)("./adTrainingModelPublish")
  .enablePlugins(JavaAppPackaging)
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.training.publish.ModelPublisher"),
    libraryDependencies ++= Seq(cats, catsEffect) ++ configDependencies
  )
  .dependsOn(configuration, protobufs, kafkaSupport)

lazy val adModelServer = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-ad-model-server", stage, executableScriptName, "tensorflow/tensorflow:latest-devel")("./admodelserver")
  .enablePlugins(JavaAppPackaging)
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.modelserver.AkkaModelServer"),
    libraryDependencies ++= kafkaBaseDependencies ++ akkaServerDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration, influxSupport)

lazy val adSpeculativeModelServer = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-ad-speculative-model-server", stage, executableScriptName, "tensorflow/tensorflow:latest-devel")("./adspeculativemodelserver")
  .enablePlugins(JavaAppPackaging)
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.speculativemodelserver.modelserver.AkkaModelServer"),
    libraryDependencies ++= kafkaBaseDependencies ++ akkaServerDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration, influxSupport)

lazy val anomalyDetection = (project in file("."))
  .settings(publish := { })
  .aggregate(protobufs, adpublisher, adModelServer, adSpeculativeModelServer,  influxSupport,
      configuration, adTrainingDataIngestion, adTrainingModelPublish, kafkaSupport, model)

