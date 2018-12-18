import sbtassembly.MergeStrategy

name := "fdp-bigdl-vggcifar"

// global settings for this build
version in ThisBuild := CommonSettings.version 
organization in ThisBuild := CommonSettings.organization
scalaVersion in ThisBuild := "2.11.12"

val spark = "2.2.0"
val bigdl = "0.6.0"

lazy val commonSettings = Seq(
  resolvers ++= Seq(
      Resolver.mavenLocal
    , "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
    , Resolver.sonatypeRepo("releases")
    , Resolver.sonatypeRepo("snapshots")
  ),
  scalacOptions ++= Seq(
    "-feature",
    "-unchecked",
    "-language:higherKinds",
    "-language:postfixOps",
    "-deprecation"
  ),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  libraryDependencies ++= Seq(
      "com.intel.analytics.bigdl"          % "bigdl-SPARK_2.2"   % bigdl, //  exclude("com.intel.analytics.bigdl", "bigdl-core"),
      "org.apache.spark"                  %% "spark-core"        % spark % "provided",
      "org.apache.spark"                  %% "spark-mllib"       % spark % "provided",
      "org.apache.spark"                  %% "spark-sql"         % spark % "provided",
      "org.rauschig"                       % "jarchivelib"       % "0.7.1"
    )
)

// standalone run of the vgg training
// 1. $ sbt assembly 
// 2. $ sbt docker 
// 3. $ sbt run --master local[4] -f /tmp/cifar-10-batches-bin --download /tmp -b 16
lazy val bigdlSample = DockerProjectSpecificAssemblyPlugin.sbtdockerAssemblySparkBase("fdp-bigdl-vggcifar", 
  assembly/*, dockerSparkBaseImageForK8s = "lightbend/spark:k8s-rc-ubuntu"*/)(".")

  .settings(commonSettings: _*)

  .settings (

    excludeDependencies ++= Seq(
      "org.spark-project.spark" % "unused"
    ),

    mainClass in Compile := Some("com.lightbend.fdp.sample.bigdl.TrainVGG")
  )
