import sbtassembly.MergeStrategy

name := "fdp-nw-intrusion"

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

// settings for an assembly based docker project based on sbt-docker plugin
def sbtdockerSparkAppBase(id: String)(base: String = id) = projectBase(id)(base)
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(
    dockerfile in docker := {

      val artifact: File = assembly.value
      if (System.getProperty("K8S_OR_DCOS") == "K8S") {
        val artifactTargetPath = s"/opt/spark/jars/${artifact.name}"

        new Dockerfile {
          from ("gcr.io/ynli-k8s/spark:v2.3.0")
          add(artifact, artifactTargetPath)
          runRaw("mkdir -p /etc/hadoop/conf")
          runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
        }
      } else {
        val artifactTargetPath = s"/opt/spark/dist/jars/${artifact.name}"

        new Dockerfile {
          from ("lightbend/spark:2.3.1-2.2.1-2-hadoop-2.6.5-01")
          add(artifact, artifactTargetPath)
          runRaw("mkdir -p /etc/hadoop/conf")
          runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
        }
      }
    },

    // Set name for the image
    imageNames in docker := Seq(
      ImageName(namespace = Some(organization.value),
        repository = (if (System.getProperty("K8S_OR_DCOS") == "K8S") s"${name.value.toLowerCase}-k8s"
          else name.value.toLowerCase), 
        tag = Some(version.value))
    ),

    buildOptions in docker := BuildOptions(cache = false)
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

// standalone run of the data ingestion application
// $ sbt run ..
lazy val ingestRun = (project in file("./ingestion"))
  .settings(Common.settings: _*)
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
lazy val ingestPackage = sbtdockerAppBase("ingestPackage")("build/ingestion")
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
lazy val anomalyDetection = sbtdockerSparkAppBase("anomalyDetection")("./anomaly")

  .settings(Common.settings: _*)
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
lazy val batchKMeans = sbtdockerSparkAppBase("batchKMeans")("./batchkmeans")

  .settings(Common.settings: _*)
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
