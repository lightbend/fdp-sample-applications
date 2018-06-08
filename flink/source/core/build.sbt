import sbtassembly.MergeStrategy

name in ThisBuild := "fdp-flink-taxiride"

// global settings for this build
version in ThisBuild := "1.2.0"
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
      val artifactTargetPath = s"/flink-1.4.2/app/jars/${artifact.name}"

      new Dockerfile {
        from ("mesosphere/dcos-flink:1.4.2-1.0")
        add(artifact, artifactTargetPath)
        runRaw("mkdir -p /flink-1.4.2/app/jars")
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
lazy val ingestRun = (project in file("./ingestion"))
  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Dependencies.ingestion)

  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.ingestion.DataIngestion"),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml"),
    addCommandAlias("ingest", "ingestRun/run")
  )


// packaged run of the data ingestion application
// 1. $ sbt universal:packageZipTarball
// 2. $ sbt docker
lazy val ingestTaxiRidePackage = sbtdockerAppBase("ingestTaxiRidePackage")("build/ingestion")
  .enablePlugins(JavaAppPackaging)
  .settings(
    resourceDirectory in Compile := (resourceDirectory in (ingestRun, Compile)).value,

    mappings in Universal ++= {
      Seq(((resourceDirectory in Compile).value / "application.conf") -> "conf/application.conf") ++
        Seq(((resourceDirectory in Compile).value / "logback.xml") -> "conf/logback.xml") ++
        Seq(((resourceDirectory in Compile).value / "log4j.properties") -> "conf/log4j.properties")
    },

    excludeFilter in Compile := "application.conf" || "logback.xml" || "log4j.properties",

    assemblyMergeStrategy in assembly := {
      case PathList("application.conf") => MergeStrategy.discard
      case PathList("logback.xml") => MergeStrategy.discard
      case PathList("log4j.properties") => MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },

    scriptClasspath := Seq("../conf/") ++ scriptClasspath.value,
    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.ingestion.DataIngestion")
  )
  .dependsOn(ingestRun)



// standalone run of the anomaly detection application
// 1. $ sbt assembly 
// 2. $ sbt docker 
// 3. $ sbt run --broker-list localhost:9092 --inTopic taxiin --outTopic taxiOut
lazy val taxiRideApp = sbtdockerFlinkAppBase("taxiRideApp")("./app")

  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Dependencies.app)

  .settings (

    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.app.TravelTimePrediction"),

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


lazy val root = (project in file(".")).
    aggregate(ingestRun, ingestTaxiRidePackage, taxiRideApp)
