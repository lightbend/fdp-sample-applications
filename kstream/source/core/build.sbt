import sbtassembly.MergeStrategy
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

val circeVersion = "0.8.0"
val catsVersion = "0.9.0"
val configVersion = "1.3.1"
val kafkaVersion = "0.10.2.1"
val confluentKafkaVersion = "3.2.2"
val specs2Version = "3.8.9"
val scalaCheckVersion = "1.12.4"
val scalaLoggingVersion = "3.5.0"
val logbackVersion = "1.2.3"
val akkaVersion = "2.5.3"
val akkaHttpVersion = "10.0.9"
val akkaHttpCirceVersion = "1.17.0"
val algebirdVersion = "0.13.0"
val chillVersion = "0.9.2"
val alpakkaFileVersion = "0.10"
val reactiveKafkaVersion = "0.16"
val bijectionVersion = "0.9.5"

val scalaVer = "2.12.2"

name in ThisBuild := "fdp-kstream"

organization in ThisBuild := "lightbend"

scalaVersion in ThisBuild := scalaVer

resolvers += "Confluent Maven" at "http://packages.confluent.io/maven/"

(sourceDirectory in avroConfig) := baseDirectory.value / "src/main/resources/com/lightbend/fdp/sample/kstream/"
(stringType in avroConfig) := "String"

enablePlugins(JavaAppPackaging)
enablePlugins(DeploySSH)

def appProject(id: String)(base:String = id) = Project(id, base = file(base))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DeploySSH)

// the main application
lazy val app = appProject("app")(".")
  .settings(
    scalaVersion := scalaVer,
    libraryDependencies ++= Seq(
      "org.apache.kafka"              % "kafka-streams"            % kafkaVersion,
      "io.confluent"                  % "kafka-avro-serializer"    % confluentKafkaVersion exclude("org.slf4j", "slf4j-log4j12"),
      "com.twitter"                  %% "bijection-avro"           % bijectionVersion,
      "com.typesafe"                  % "config"                   % configVersion,
      "com.typesafe.scala-logging"   %% "scala-logging"            % scalaLoggingVersion,
      "org.typelevel"                %% "cats"                     % catsVersion,
      "io.circe"                     %% "circe-core"               % circeVersion,
      "io.circe"                     %% "circe-generic"            % circeVersion,
      "io.circe"                     %% "circe-parser"             % circeVersion,
      "com.twitter"                  %% "algebird-core"            % algebirdVersion,
      "com.twitter"                  %% "chill"                    % chillVersion,
      "com.typesafe.akka"            %% "akka-slf4j"               % akkaVersion,
      "com.typesafe.akka"            %% "akka-stream"              % akkaVersion,
      "com.typesafe.akka"            %% "akka-http"                % akkaHttpVersion,
      "de.heikoseeberger"            %% "akka-http-circe"          % akkaHttpCirceVersion,
      "com.lightbend.akka"           %% "akka-stream-alpakka-file" % alpakkaFileVersion,
      "com.typesafe.akka"            %% "akka-stream-kafka"        % reactiveKafkaVersion,
      "ch.qos.logback"                % "logback-classic"          % logbackVersion,
      "org.specs2"                   %% "specs2-core"              % specs2Version       % Test,
      "org.specs2"                   %% "specs2-scalacheck"        % specs2Version       % Test,
      "org.scalacheck"               %% "scalacheck"               % scalaCheckVersion   % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
      "-encoding", "utf-8",                // Specify character encoding used by source files.
      "-explaintypes",                     // Explain type errors in more detail.
      "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
      "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
      "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
      "-language:higherKinds",             // Allow higher-kinded types
      "-language:implicitConversions",     // Allow definition of implicit functions called views
      "-language:postfixOps",              // Allow postfix operator
      "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
      "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
      "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
      "-Xfuture",                          // Turn on future language features.
      "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
      "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
      "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
      "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
      "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
      "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
      "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
      "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
      "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
      "-Xlint:option-implicit",            // Option.apply used implicit view.
      "-Xlint:package-object-classes",     // Class or object defined in package object.
      "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
      "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
      "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
      "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
      "-Xlint:unsound-match",              // Pattern match may not be typesafe.
      "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
      "-Ypartial-unification",             // Enable partial unification in type constructor inference
      "-Ywarn-dead-code",                  // Warn when dead code is identified.
      "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
      "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
      "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
      "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
      "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
      // "-Ywarn-unused:locals",              // Warn if a local definition is unused.
      // "-Ywarn-unused:params",              // Warn if a value parameter is unused.
      // "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
      // "-Ywarn-unused:privates",            // Warn if a private member is unused.
      "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
    ),
    assemblyMergeStrategy in assembly := {
      case PathList("application_proc.conf") => MergeStrategy.discard
      case PathList("application_dsl.conf") => MergeStrategy.discard
      case PathList("logback-dsl.xml") => MergeStrategy.discard
      case PathList("logback-proc.xml") => MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

// an sbt project to make it easier to run WeblogProcessing
lazy val dslRun = project
  .in(file("run/dsl"))
  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogProcessing"),
    resourceDirectory in Compile := (resourceDirectory in (app, Compile)).value,
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application_dsl.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback-dsl.xml"),
    addCommandAlias("dsl", "dslRun/run")
  )
  .dependsOn(app)

// the package for WeblogProcessing that uses Kafka-Streams DSL
lazy val dslPackage = appProject("dslPackage")("build/dsl")
  .settings(
    scalaVersion := scalaVer,
    resourceDirectory in Compile := (resourceDirectory in (app, Compile)).value,
    mappings in Universal ++= {
      Seq(((resourceDirectory in Compile).value / "application_dsl.conf") -> "conf/application.conf") ++
        Seq(((resourceDirectory in Compile).value / "logback-dsl.xml") -> "conf/logback.xml")
    },
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
    ),
    scriptClasspath := Seq("../conf/") ++ scriptClasspath.value,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogProcessing")
  )
  .dependsOn(app)

// an sbt project to make it easier to run WeblogDriver
lazy val procRun = project
  .in(file("run/proc"))
  .settings (
    fork in run := true,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogDriver"),
    resourceDirectory in Compile := (resourceDirectory in (app, Compile)).value,
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + (resourceDirectory in Compile).value / "application_proc.conf",
      "-Dlogback.configurationFile=" + (resourceDirectory in Compile).value / "logback-proc.xml"),
    addCommandAlias("proc", "procRun/run")
  )
  .dependsOn(app)

// the package for WeblogDriver that uses Kafka-Streams Processor APIs
lazy val procPackage = appProject("procPackage")("build/proc")
  .settings(
    scalaVersion := scalaVer,
    resourceDirectory in Compile := (resourceDirectory in (app, Compile)).value,
    mappings in Universal ++= {
      Seq(((resourceDirectory in Compile).value / "application_proc.conf") -> "conf/application.conf") ++
        Seq(((resourceDirectory in Compile).value / "logback-proc.xml") -> "conf/logback.xml")
    },
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
      ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
    ),
    scriptClasspath := Seq("../conf/") ++ scriptClasspath.value,
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.WeblogDriver")
  )
  .dependsOn(app)
