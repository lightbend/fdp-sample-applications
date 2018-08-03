import sbtassembly.MergeStrategy

name := "fdp-nw-intrusion"

// global settings for this build
version in ThisBuild := CommonSettings.version 
organization in ThisBuild := CommonSettings.organization
scalaVersion in ThisBuild := Versions.scalaVersion

// standalone run of the data ingestion application
// $ sbt run ..
lazy val ingestRun = (project in file("./ingestion"))
  .settings(libraryDependencies ++= Dependencies.ingestionDependencies)

  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.nwintrusion.ingestion.TransformIntrusionData"),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml"),
    addCommandAlias("ingest", "ingestRun/run")
  )


// packaged run of the data ingestion application
// 1. $ sbt universal:packageZipTarball
// 2. $ sbt docker
lazy val ingestPackage = DockerProjectSpecificPackagerPlugin.sbtdockerPackagerBase("fdp-nwintrusion-ingestion", stage, executableScriptName)("build/ingestion")
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
    mainClass in Compile := Some("com.lightbend.fdp.sample.nwintrusion.ingestion.TransformIntrusionData")
  )
  .dependsOn(ingestRun)


// standalone run of the anomaly detection application
// 1. $ sbt assembly 
// 2. $ sbt docker 
// 3. $ sbt run ..
lazy val anomalyDetection = DockerProjectSpecificAssemblyPlugin.sbtdockerAssemblySparkBase("fdp-nwintrusion-anomaly", assembly)("./anomaly")

  .settings(libraryDependencies ++= Dependencies.anomalyDependencies)

  .settings (

    excludeDependencies ++= Seq(
      "org.spark-project.spark" % "unused"
    ),

    mainClass in Compile := Some("com.lightbend.fdp.sample.nwintrusion.anomaly.SparkClustering"),

    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml")
  )
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
    dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")


// standalone run of the batch k-means application
// 1. $ sbt assembly 
// 2. $ sbt docker 
// 3. $ sbt run ..
lazy val batchKMeans = DockerProjectSpecificAssemblyPlugin.sbtdockerAssemblySparkBase("fdp-nwintrusion-batchkmeans", assembly)("./batchkmeans")

  .settings(libraryDependencies ++= Dependencies.batchKMeansDependencies)

  .settings (

    excludeDependencies ++= Seq(
      "org.spark-project.spark" % "unused"
    ),

    mainClass in Compile := Some("com.lightbend.fdp.sample.nwintrusion.batchkmeans.BatchKMeans"),

    javaOptions in run ++= Seq(
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback.xml")
  )
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7",
    dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")


lazy val root = (project in file(".")).
    aggregate(ingestRun, ingestPackage, anomalyDetection, batchKMeans)
