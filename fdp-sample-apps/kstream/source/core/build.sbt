import sbtassembly.MergeStrategy

// global settings for this build
name in ThisBuild := "fdp-kstream"
version in ThisBuild := "1.2.1"
organization in ThisBuild := "lightbend"
scalaVersion in ThisBuild := Versions.scalaVersion

resolvers += "Confluent Maven" at "http://packages.confluent.io/maven/"

// base project settings
def projectBase(id: String)(base: String = id) = Project(id, base = file(base))
  .enablePlugins(JavaAppPackaging)
  .settings(
    fork in run := true
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


// standalone run of the dsl example application
lazy val dslRun = (project in file("./example-dsl"))
  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Dependencies.dslDependencies)
  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogProcessing"),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application-dsl.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback-dsl.xml",
      "-Dlog4j.configurationFile=" + (resourceDirectory in Compile).value / "log4j.properties"),
    (sourceDirectory in AvroConfig) := baseDirectory.value / "src/main/resources/com/lightbend/fdp/sample/kstream/",
    (stringType in AvroConfig) := "String",
    addCommandAlias("dsl", "dslRun/run")
  )
  .dependsOn(server)

// packaged run of the dsl example application
lazy val dslPackage = sbtdockerAppBase("fdp-kstream-dsl")("build/dsl")
  .settings(
    resourceDirectory in Compile := (resourceDirectory in (dslRun, Compile)).value,
    mappings in Universal ++= {
      Seq(((resourceDirectory in Compile).value / "application-dsl.conf") -> "conf/application.conf") ++
        Seq(((resourceDirectory in Compile).value / "logback-dsl.xml") -> "conf/logback.xml") ++
        Seq(((resourceDirectory in Compile).value / "log4j.properties") -> "conf/log4j.properties")
    },
    assemblyMergeStrategy in assembly := {
      case PathList("application-dsl.conf") => MergeStrategy.discard
      case PathList("logback-dsl.xml") => MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    (sourceDirectory in AvroConfig) := baseDirectory.value / "src/main/resources/com/lightbend/fdp/sample/kstream/",
    (stringType in AvroConfig) := "String",
    scriptClasspath := Seq("../conf/") ++ scriptClasspath.value,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogProcessing")
  )
  .dependsOn(server, dslRun)

// standalone run of the proc example application
lazy val procRun = (project in file("./example-proc"))
  .settings(Common.settings: _*)
  .settings(libraryDependencies ++= Dependencies.procDependencies)
  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogDriver"),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application-proc.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback-proc.xml",
      "-Dlog4j.configurationFile=" + (resourceDirectory in Compile).value / "log4j.properties"),
    addCommandAlias("proc", "procRun/run")
  )
  .dependsOn(server)

// packaged run of the proc example application
lazy val procPackage = sbtdockerAppBase("fdp-kstream-processor")("build/proc")
  .settings(
    scalaVersion := Versions.scalaVersion,
    resourceDirectory in Compile := (resourceDirectory in (procRun, Compile)).value,
    mappings in Universal ++= {
      Seq(((resourceDirectory in Compile).value / "application-proc.conf") -> "conf/application.conf") ++
        Seq(((resourceDirectory in Compile).value / "logback-proc.xml") -> "conf/logback.xml") ++
        Seq(((resourceDirectory in Compile).value / "log4j.properties") -> "conf/log4j.properties")
    },
    assemblyMergeStrategy in assembly := {
      case PathList("application-proc.conf") => MergeStrategy.discard
      case PathList("logback-proc.xml") => MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    scriptClasspath := Seq("../conf/") ++ scriptClasspath.value,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogDriver")
  )
  .dependsOn(server, procRun)

lazy val server = (project in file("./kafka-local-server")).
    settings(Common.settings: _*).
    settings(libraryDependencies ++= Dependencies.serverDependencies)

lazy val root = (project in file(".")).
    aggregate(dslRun, dslPackage, procRun, procPackage, server)
