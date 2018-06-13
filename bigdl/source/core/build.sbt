import sbtassembly.MergeStrategy

name := "bigdlvgg"

// global settings for this build
version in ThisBuild := "1.2.0"
organization in ThisBuild := "lightbend"
scalaVersion in ThisBuild := "2.11.8"

val spark = "2.2.0"
val bigdl = "0.5.0"

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
      "com.intel.analytics.bigdl"          % "bigdl-SPARK_2.2"   % bigdl exclude("com.intel.analytics.bigdl", "bigdl-core"),
      "org.apache.spark"                  %% "spark-core"        % spark % "provided",
      "org.apache.spark"                  %% "spark-mllib"       % spark % "provided",
      "org.apache.spark"                  %% "spark-sql"         % spark % "provided",
      "org.rauschig"                       % "jarchivelib"       % "0.7.1"
    )
)

// base project settings
def projectBase(id: String)(base: String = id) = Project(id, base = file(base))
  .settings(
    fork in run := true,

    assemblyMergeStrategy in assembly := {
      case x if x.contains("com/intel/analytics/bigdl/bigquant/") => MergeStrategy.first
      case x if x.contains("com/intel/analytics/bigdl/mkl/") => MergeStrategy.first
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
      val artifactTargetPath = s"/opt/spark/dist/jars/${artifact.name}"

      new Dockerfile {
        from ("lightbend/spark:2.3.1-2.2.1-2-hadoop-2.6.5-01")
        add(artifact, artifactTargetPath)
        runRaw("mkdir -p /etc/hadoop/conf")
        runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
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

// standalone run of the vgg training
// 1. $ sbt assembly 
// 2. $ sbt docker 
// 3. $ sbt run --master local[4] -f /tmp/cifar-10-batches-bin --download /tmp -b 16
lazy val bigdlSample = sbtdockerSparkAppBase("bigdlSample")(".")

  .settings(commonSettings: _*)

  .settings (

    excludeDependencies ++= Seq(
      "org.spark-project.spark" % "unused"
    ),

    mainClass in Compile := Some("com.lightbend.fdp.sample.bigdl.TrainVGG")
  )
