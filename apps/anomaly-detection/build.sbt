name := "AnomalyDetection"

import Dependencies._

scalaVersion in ThisBuild := Versions.Scala
version in ThisBuild := "1.3.1"
organization in ThisBuild := "lightbend"


// settings for a native-packager based docker scala project based on sbt-docker plugin
def sbtdockerScalaAppBase(id: String)(base: String = id) = Project(id, base = file(base))
  .enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)
  .settings(
    dockerfile in docker := {
      val appDir = stage.value
      val targetDir = s"/$base"

      new Dockerfile {
        from("openjdk:8u151-jre")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir)
      }
    },

    // Set name for the image
    imageNames in docker := Seq(
      ImageName(namespace = Some(organization.value),
        repository = name.value.toLowerCase,
        tag = Some(version.value))
    ),

    buildOptions in docker := BuildOptions(cache = false)
  )

// settings for a native-packager based docker tensorflow project based on sbt-docker plugin
def sbtdockerTensorflowAppBase(id: String)(base: String = id) = Project(id, base = file(base))
  .enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)
  .settings(
    dockerfile in docker := {
      val appDir = stage.value
      val targetDir = s"/$base"

      new Dockerfile {
        from("tensorflow/tensorflow:latest-devel")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir)
      }
    },

    // Set name for the image
    imageNames in docker := Seq(
      ImageName(namespace = Some(organization.value),
        repository = name.value.toLowerCase,
        tag = Some(version.value))
    ),

    buildOptions in docker := BuildOptions(cache = false)
  )

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
lazy val adpublisher = sbtdockerScalaAppBase("adpublisher")("./adpublisher")
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.publish.DataProvider"),
    libraryDependencies ++= kafkaBaseDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(protobufs, configuration, influxSupport, kafkaSupport)

// Ingestion project - pure scala
lazy val adTrainingDataIngestion = sbtdockerScalaAppBase("trainingdataingestion")("./training_data_ingestion")
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.training.ingestion.TrainingIngestion"),
    libraryDependencies ++= Seq(influxDBClient, cats, monix) ++ configDependencies
  )
  .dependsOn(configuration, influxSupport, kafkaSupport)

// Model publishing project - pure scala
lazy val adTrainingModelPublish = sbtdockerScalaAppBase("trainingmodelpublish")("./training_model_publish")
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.training.publish.ModelPublisher"),
    libraryDependencies ++= Seq(cats, catsEffect) ++ configDependencies
  )
  .dependsOn(configuration, protobufs, kafkaSupport)

lazy val adModelServer = sbtdockerTensorflowAppBase("admodelserver")("./admodelserver")
  .settings(Common.settings: _*)
  .settings (
    mainClass in Compile := Some("com.lightbend.ad.modelserver.AkkaModelServer"),
    libraryDependencies ++= kafkaBaseDependencies ++ akkaServerDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration, influxSupport)

lazy val adSpeculativeModelServer = sbtdockerTensorflowAppBase("adspeculativemodelserver")("./adspeculativemodelserver")
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

