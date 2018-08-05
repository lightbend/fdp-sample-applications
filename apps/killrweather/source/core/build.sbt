import Dependencies._

version in ThisBuild := CommonSettings.version 
organization in ThisBuild := CommonSettings.organization
scalaVersion in ThisBuild := Versions.Scala

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
  .settings(libraryDependencies ++= core)

// Spark streaming project
lazy val killrWeatherApp = DockerProjectSpecificAssemblyPlugin.sbtdockerAssemblySparkBase("fdp-killrweather-app", assembly, dockerSparkBaseImage = "lightbend/spark:k8s-rc")("./killrweather-app")
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
lazy val killrWeatherApp_structured = DockerProjectSpecificAssemblyPlugin.sbtdockerAssemblySparkBase("fdp-killrweather-structured-app", assembly, dockerSparkBaseImage = "lightbend/spark:k8s-rc")("./killrweather-app_structured")
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
    libraryDependencies ++= spark.map(_.withConfigurations(configurations = Option("compile"))) ++ Seq(influxDBClient)
  )
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
    dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
  .dependsOn(killrWeatherApp)

lazy val appLocalRunnerstructured = (project in file("./killrweather-structured-app-local"))
  .settings(
    libraryDependencies ++= sparkStructured.map(_.withConfigurations(configurations = Option("compile"))) ++ Seq(influxDBClient)
  )
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7")
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
  .dependsOn(killrWeatherApp_structured)

// Killrweather Beam - for now only build locally
lazy val killrWeatherApp_beam = (project in file("./killrweather-beam"))
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
lazy val loader = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-killrweather-loader", stage, executableScriptName)("./killrweather-loader")
  .enablePlugins(JavaAppPackaging)
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.loader.kafka.KafkaDataIngester"),
    libraryDependencies ++= loaders,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(killrWeatherCore, protobufs)

// httpClient - exposing HTTP listener for weather data - pure scala
lazy val httpclient = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-killrweather-httpclient", stage, executableScriptName)("./killrweather-httpclient")
  .enablePlugins(JavaAppPackaging)
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.client.http.RestAPIs"),
    libraryDependencies ++= clientHTTP,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(killrWeatherCore, protobufs)

// GRPC - exposing GRPC listener for weather data - pure scala
lazy val grpcclient = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-killrweather-grpcclient", stage, executableScriptName)("./killrweather-grpclient")
  .enablePlugins(JavaAppPackaging)
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.client.grpc.WeatherGRPCClient"),
    libraryDependencies ++= clientGRPC,
    bashScriptExtraDefines += """addJava "-Dconfig.resource=cluster.conf""""
  )
  .dependsOn(killrWeatherCore, protobufs)

lazy val killrweather = (project in file("."))
  .aggregate(killrWeatherCore, killrWeatherApp, killrWeatherApp_structured, httpclient, grpcclient, loader, protobufs)

