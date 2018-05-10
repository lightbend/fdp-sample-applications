import Dependencies._

scalaVersion in ThisBuild := Versions.Scala
version in ThisBuild := "1.2.0"
organization in ThisBuild := "lightbend"
val K8S_OR_DCOS = ""//"K8S"


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

def sbtdockerSparkAppBase(id: String)(base: String = id) = Project(id, base = file(base))
  .enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)
  .settings(
    dockerfile in docker := {
      val artifact: File = assembly.value
      val artifactTargetPath = s"/opt/spark/jars/${artifact.name}"

      K8S_OR_DCOS match {
        case "K8S" =>
          new Dockerfile {
            from ("gcr.io/ynli-k8s/spark:v2.3.0")           // K8
            add(artifact, artifactTargetPath)
            runRaw("mkdir -p /etc/hadoop/conf")
            runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
          }

        case _ =>
          new Dockerfile {
            from ("mesosphere/spark:2.3.0-2.2.1-2-hadoop-2.6")    // DC/OS
            add(artifact, artifactTargetPath)
            runRaw("mkdir -p /etc/hadoop/conf")
            runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
          }
      }
    },

    // Set name for the image
    imageNames in docker := Seq(
      ImageName(namespace = Some(organization.value),
        repository = if (K8S_OR_DCOS =="K8S") s"${name.value.toLowerCase}-k8s" else name.value.toLowerCase,
        tag = Some(version.value))
    ),

    buildOptions in docker := BuildOptions(cache = false)
  )


lazy val protobufs = (project in file("./protobufs"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ))
  .settings(libraryDependencies ++= grpc)
  .settings(dependencyOverrides += "io.netty" % "netty-codec-http2" % "4.1.11.Final")
  .settings(dependencyOverrides += "io.netty" % "netty-handler-proxy" % "4.1.11.Final")

// Supporting project - used as an internal library
lazy val killrWeatherCore = (project in file("./killrweather-core"))
  .settings(defaultSettings:_*)
  .settings(libraryDependencies ++= core)

// Spark streaming project
lazy val killrWeatherApp = sbtdockerSparkAppBase("killrWeatherApp")("./killrweather-app")
  .settings(defaultSettings:_*)
  .settings(libraryDependencies ++= app)
  .settings (mainClass in Compile := Some("com.lightbend.killrweather.app.KillrWeather"))
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
            dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
  .settings(
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
  .dependsOn(killrWeatherCore, protobufs)

// Spark structured streaming project
lazy val killrWeatherApp_structured = sbtdockerSparkAppBase("killrWeatherApp_structured")("./killrweather-app_structured")
  .settings(defaultSettings:_*)
  .settings(libraryDependencies ++= appStructured)
  .settings (mainClass in Compile := Some("com.lightbend.killrweather.app.structured.KillrWeatherStructured"))
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
            dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7",
            dependencyOverrides += "org.json4s" % "json4s-ast_2.11" % "3.2.11",
            dependencyOverrides += "org.json4s" % "json4s-core_2.11" % "3.2.11",
            dependencyOverrides += "org.json4s" % "json4s-jackson_2.11" % "3.2.11"
  )
  .settings(
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
  .dependsOn(killrWeatherCore, protobufs)

// Supporting projects to enable running spark projects locally
lazy val appLocalRunner = (project in file("./killrweather-app-local"))
  .settings(
    libraryDependencies ++= spark.map(_.copy(configurations = Option("compile"))) ++ Seq(influxDBClient)
  )
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
    dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
  .dependsOn(killrWeatherApp)

lazy val appLocalRunnerstructured = (project in file("./killrweather-structured-app-local"))
  .settings(
    libraryDependencies ++= sparkStructured.map(_.copy(configurations = Option("compile"))) ++ Seq(influxDBClient)
  )
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7")
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
  .dependsOn(killrWeatherApp_structured)

// Killrweather Beam - for now only build locally
lazy val killrWeatherApp_beam = (project in file("./killrweather-beam"))
  .settings(defaultSettings:_*)
  .settings(mainClass in Compile := Some("com.lightbend.killrweather.beam.KillrWeatherBeam"))
  .settings(libraryDependencies ++= beamDependencies)
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
            dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7",
            dependencyOverrides += "org.json4s" % "json4s-ast_2.11" % "3.2.11",
            dependencyOverrides += "org.json4s" % "json4s-core_2.11" % "3.2.11",
            dependencyOverrides += "org.json4s" % "json4s-jackson_2.11" % "3.2.11"
  )
  .dependsOn(killrWeatherCore, protobufs)

// Loader - loading weather data to Kafka - pure scala
lazy val loader = sbtdockerScalaAppBase("loader")("./killrweather-loader")
  .settings(defaultSettings:_*)
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.loader.kafka.KafkaDataIngester"),
    libraryDependencies ++= loaders,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(killrWeatherCore, protobufs)

// httpClient - exposing HTTP listener for weather data - pure scala
lazy val httpclient = sbtdockerScalaAppBase("httpclient")("./killrweather-httpclient")
  .settings(defaultSettings:_*)
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.client.http.RestAPIs"),
    libraryDependencies ++= clientHTTP,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(killrWeatherCore, protobufs)

// GRPC - exposing GRPC listener for weather data - pure scala
lazy val grpcclient = sbtdockerScalaAppBase("grpcclient")("./killrweather-grpclient")
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.client.grpc.WeatherGRPCClient"),
    libraryDependencies ++= clientGRPC,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(killrWeatherCore, protobufs)

lazy val killrweather = (project in file("."))
  .aggregate(killrWeatherCore, killrWeatherApp, killrWeatherApp_structured, httpclient, grpcclient, loader, protobufs)

