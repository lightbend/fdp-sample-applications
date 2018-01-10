import sbtassembly.MergeStrategy
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

val spark = "2.2.0"

allowSnapshot in ThisBuild := true

enablePlugins(DeploySSH)

val repo = "http://repo1.maven.org/maven2"
def mkl_native(os: String): String = {
  s"${repo}/com/intel/analytics/bigdl/native/mkl-java-${os}/0.3.0/mkl-java-${os}-0.3.0.jar"
}

def bigquant_native(os: String): String = {
  s"${repo}/com/intel/analytics/bigdl/bigquant/bigquant-java-${os}/0.3.0/bigquant-java-${os}-0.3.0.jar"
}

lazy val commonSettings = Seq(
  resolvers ++= Seq(
      Resolver.mavenLocal
    , "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
    , Resolver.sonatypeRepo("releases")
    , Resolver.sonatypeRepo("snapshots")
  ),
  scalaVersion := "2.11.8",
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  libraryDependencies ++= Seq(
      "com.intel.analytics.bigdl"          % "bigdl-SPARK_2.2"   % "0.3.0" exclude("com.intel.analytics.bigdl", "bigdl-core"),
//    "com.intel.analytics.bigdl.native"   % "mkl-java"          % "0.3.0", // comment for Mac
//    "com.intel.analytics.bigdl.bigquant" % "bigquant-java"     % "0.3.0", // comment for Mac
      "com.intel.analytics.bigdl.native"   % "mkl-java-mac"      % "0.3.0" from mkl_native("mac"), // uncomment for Mac
      "com.intel.analytics.bigdl.bigquant" % "bigquant-java-mac" % "0.3.0" from bigquant_native("mac"), // uncomment for Mac
      "org.apache.spark"                  %% "spark-core"        % spark % "provided",
      "org.apache.spark"                  %% "spark-mllib"       % spark % "provided",
      "org.apache.spark"                  %% "spark-sql"         % spark % "provided",
      "org.rauschig"                       % "jarchivelib"       % "0.7.1"
    )
)

mainClass in assembly := Some("com.lightbend.fdp.sample.bigdl.TrainVGG")

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "bigdlsample",
    scalacOptions ++= Seq(
      "-feature",
      "-unchecked",
      "-language:higherKinds",
      "-language:postfixOps",
      "-deprecation"
    )
  )

//some exclusions and merge strategies for assembly
excludeDependencies ++= Seq(
  "org.spark-project.spark" % "unused"
)

assemblyMergeStrategy in assembly := {
  case x if x.contains("com/intel/analytics/bigdl/bigquant/") => MergeStrategy.first
  case x if x.contains("com/intel/analytics/bigdl/mkl/") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

deployResourceConfigFiles ++= Seq("deploy.conf")

deployArtifacts ++= Seq(
  ArtifactSSH(assembly.value, "/var/www/html/")
)
