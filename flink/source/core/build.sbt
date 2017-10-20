import Dependencies._
import sbtassembly.MergeStrategy, sbtassembly.Assembly
import sbtenforcer.EnforcerPlugin
import NativePackagerHelper._
import deployssh.DeploySSH._

// NOTE: Versioning of all artifacts is under the control of the `sbt-dynver` plugin and
// enforced by `EnforcerPlugin` found in the `build-plugin` directory.
//
// sbt-dynver: https://github.com/dwijnand/sbt-dynver
//
// The versions emitted follow the following rules:
// |  allowSnapshot  | Case                                                                 | version                        |
// |-----------------| -------------------------------------------------------------------- | ------------------------------ |
// | false (default) | when on tag v1.0.0, w/o local changes                                | 1.0.0                          |
// | true            | when on tag v1.0.0 with local changes                                | 1.0.0+20140707-1030            |
// | true            | when on tag v1.0.0 +3 commits, on commit 1234abcd, w/o local changes | 1.0.0+3-1234abcd               |
// | true            | when on tag v1.0.0 +3 commits, on commit 1234abcd with local changes | 1.0.0+3-1234abcd+20140707-1030 |
// | true            | when there are no tags, on commit 1234abcd, w/o local changes        | 1234abcd                       |
// | true            | when there are no tags, on commit 1234abcd with local changes        | 1234abcd+20140707-1030         |
// | true            | when there are no commits, or the project isn't a git repo           | HEAD+20140707-1030             |
//
// This means DO NOT set or define a `version := ...` setting.
//
// If you have pending changes or a missing tag on the HEAD you will need to set
// `allowSnapshot` to true in order to run `packageBin`.  Otherwise you will get an error
// with the following information:
//   ---------------
// 1. You have uncommmited changes (unclean directory) - Fix: commit your changes and set a tag on HEAD.
// 2. You have a clean directory but no tag on HEAD - Fix: tag the head with a version that satisfies the regex: 'v[0-9][^+]*'
// 3. You have uncommmited changes (a dirty directory) but have not set `allowSnapshot` to `true` - Fix: `set (allowSnapshot in ThisBuild) := true`""".stripMargin)

allowSnapshot in ThisBuild := true

name in ThisBuild := "fdp-flink-taxiride"

organization in ThisBuild := "lightbend"

val scalaVer = "2.11.8"
scalaVersion in ThisBuild := "2.11.8"

def appProject(id: String)(base:String = id) = Project(id, base = file(base))

// the main application
lazy val root = appProject("root")(".")
  .settings(defaultSettings:_*)

lazy val ingestion = appProject("ingestion")("./ingestion")
  .settings(defaultSettings:_*)
  .settings(
    scalaVersion := scalaVer,
    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.ingestion.DataIngestion"),
    maintainer := "Debasish Ghosh <debasish.ghosh@lightbend.com>",
    packageSummary := "Taxiride data ingestion",
    packageDescription := "Taxiride data ingestion",
    libraryDependencies ++= Dependencies.ingestion
  )
  .settings(
    assemblyMergeStrategy in assembly := {
      case PathList("application.conf") => MergeStrategy.discard
      case PathList("logback.xml") => MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
  .settings(
    resourceDirectory in Compile := (resourceDirectory in Compile).value,
    mappings in Universal ++= {
      Seq(((resourceDirectory in Compile).value / "application.conf") -> "conf/application.conf") ++
        Seq(((resourceDirectory in Compile).value / "logback.xml") -> "conf/logback.xml")
    },
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
    ),
    scriptClasspath := Seq("../conf/") ++ scriptClasspath.value
  )
  .dependsOn(root)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DeploySSH)

lazy val app = appProject("app")("./app")
  .settings(defaultSettings:_*)
  .settings(
    scalaVersion := scalaVer,
    mainClass in Compile := Some("com.lightbend.fdp.sample.flink.app.TravelTimePrediction"),
    maintainer := "Debasish Ghosh <debasish.ghosh@lightbend.com>",
    packageSummary := "Taxiride app",
    packageDescription := "Taxiride app",
    libraryDependencies ++= Dependencies.app
  )
  .settings(
    assemblyMergeStrategy in assembly := {
      case PathList("application.conf") => MergeStrategy.discard
      case PathList("logback.xml") => MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
  .settings(
    resourceDirectory in Compile := (resourceDirectory in Compile).value,
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH(assembly.value, "/var/www/html/")
    )
  )
  .dependsOn(root)
  .enablePlugins(DeploySSH)

