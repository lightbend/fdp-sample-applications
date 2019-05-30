import sbtassembly.MergeStrategy

name in ThisBuild := "fdp-flink-taxiride"

// global settings for this build
version in ThisBuild := "2.0.0"
organization in ThisBuild := "lightbend"
scalaVersion in ThisBuild := Versions.scalaVersion

// base project settings
def projectBase(id: String)(base: String = id) = Project(id, base = file(base))
  .settings(
    fork in run := true,

    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case PathList("org", "slf4j", xs@_*) => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

// settings for a native-packager based docker project based on sbt-docker plugin
def sbtdockerAppBase(id: String)(base: String = id) = projectBase(id)(base)
  .enablePlugins(sbtdocker.DockerPlugin)
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

// settings for an assembly based docker project based on sbt-docker plugin
def sbtdockerFlinkAppBase(id: String)(base: String = id) = projectBase(id)(base)
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(
    dockerfile in docker := {

      val artifact: File = assembly.value
      // This goes into a directory included in FLinks classpath
      val artifactTargetPath = s"/opt/flink/lib/${artifact.name}"

      new Dockerfile {
        from ("lightbend/flink:1.8.0_scala_2.11_debian")
        add(artifact, artifactTargetPath)
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

// allow circular dependencies for test sources
compileOrder in Test := CompileOrder.Mixed

// standalone run of the data ingestion application
// $ sbt run ..
lazy val ingestRun = sbtdockerAppBase("fdp-flink-ingestion")("./ingestion")
  
  .settings(Common.settings: _*)
  .enablePlugins(JavaAppPackaging)
  .settings(libraryDependencies ++= Dependencies.ingestion)

  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.ingestion.DataIngestion"),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml"),
    addCommandAlias("ingest", "ingestRun/run")
  )
  .dependsOn(support)

lazy val resultsprinter = sbtdockerAppBase("fdp-flink-resultprinter")("./resultprinter")

  .settings(Common.settings: _*)
  .enablePlugins(JavaAppPackaging)
  .settings(libraryDependencies ++= Dependencies.ingestion)

  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.reader.ResultReader"),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml"),
  )
  .dependsOn(support)

lazy val taxiRideApp = sbtdockerFlinkAppBase("fdp-flink-taxiride")("./app")

  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Dependencies.app)

  .settings (

    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.app.TravelTimePrediction"),
//    dependencyOverrides += "org.apache.kafka" % "kafka-clients"  % "2.1.0",
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml")
  )

  .settings(

    // stays inside the sbt console when we press "ctrl-c" while a Flink programme executes with "run" or "runMain"
    Compile / run / fork := true,
    Global / cancelable := true,

    // exclude Scala library from assembly
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
  )
  .dependsOn(support)

lazy val support = (project in file("./support"))
  .settings(libraryDependencies ++= Dependencies.common)

lazy val root = (project in file(".")).
    aggregate(ingestRun, support, taxiRideApp, resultsprinter)
