import sbtassembly.MergeStrategy

name in ThisBuild := "fdp-flink-taxiride"

// global settings for this build
version in ThisBuild := CommonSettings.version 
organization in ThisBuild := CommonSettings.organization
scalaVersion in ThisBuild := Versions.scalaVersion


// allow circular dependencies for test sources
compileOrder in Test := CompileOrder.Mixed

// standalone run of the data ingestion application
// $ sbt run ..
lazy val ingestRun = (project in file("./ingestion"))
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
lazy val ingestTaxiRidePackage = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-flink-ingestion", stage, executableScriptName)("build/ingestion")
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
lazy val taxiRideApp = DockerProjectSpecificAssemblyPlugin.sbtdockerAssemblyFlinkBase("fdp-flink-taxiride", assembly)("./app")

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
