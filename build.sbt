
import Dependencies._

scalaVersion in ThisBuild := Versions.Scala
version in ThisBuild := "1.2.0"
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
lazy val publisher = sbtdockerScalaAppBase("fdp-akka-kafka-streams-model-server-model-publisher")("./publisher")
  .settings (
    mainClass in Compile := Some("com.lightbend.kafka.DataProvider"),
    libraryDependencies ++= kafkaBaseDependencies ++ testDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(protobufs, configuration)

// Both Kafka and Akka services are using Tensorflow
lazy val kafkaSvc = sbtdockerTensorflowAppBase("fdp-akka-kafka-streams-model-server-kafka-streams-server")("./kafkastreamssvc")
  .settings (
    mainClass in Compile := Some("com.lightbend.modelserver.withstore.ModelServerWithStore"),
    libraryDependencies ++= kafkaDependencies ++ webDependencies,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration)

lazy val akkaSvc = sbtdockerTensorflowAppBase("fdp-akka-kafka-streams-model-server-akka-streams-server")("./akkastreamssvc")
  .settings (
    mainClass in Compile := Some("com.lightbend.modelServer.modelServer.AkkaModelServer"),
    libraryDependencies ++= kafkaDependencies ++ akkaServerDependencies ++ Seq(curator),
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(model, configuration)

lazy val modelserver = (project in file("."))
  .settings(publish := { })
  .aggregate(protobufs, publisher, model, configuration, kafkaSvc, akkaSvc)

