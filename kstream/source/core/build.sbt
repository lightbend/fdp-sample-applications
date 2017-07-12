import sbtassembly.MergeStrategy
import NativePackagerHelper._
import deployssh.DeploySSH._


val circeVersion = "0.8.0"
val catsVersion = "0.9.0"
val configVersion = "1.3.1"
val kafkaVersion = "0.10.2.1"
val specs2Version = "3.8.9" 
val scalaCheckVersion = "1.12.4"
val scalaLoggingVersion = "3.5.0"
val logbackVersion = "1.2.3"
val akkaVersion = "2.5.3"
val akkaHttpVersion = "10.0.8"
val akkaHttpCirceVersion = "1.17.0"
val algebirdVersion = "0.13.0"
val chillVersion = "0.9.2"
val alpakkaFileVersion = "0.10"
val reactiveKafkaVersion = "0.16"

val scalaVer = "2.12.2"

name in ThisBuild := "fdp-kstream"

organization in ThisBuild := "lightbend"

version in ThisBuild := "0.1"

scalaVersion in ThisBuild := scalaVer

enablePlugins(JavaAppPackaging)
enablePlugins(DeploySSH)

// the main application
lazy val app = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DeploySSH)
  .settings(
    scalaVersion := scalaVer,
    libraryDependencies ++= Seq(
      "org.apache.kafka"              % "kafka-streams"            % kafkaVersion,
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
      "-Ywarn-unused:locals",              // Warn if a local definition is unused.
      "-Ywarn-unused:params",              // Warn if a value parameter is unused.
      "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
      "-Ywarn-unused:privates",            // Warn if a private member is unused.
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

// the package for WeblogProcessing that uses Kafka-Streams DSL
lazy val dslPackage = project
  .in(file("build/dsl"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DeploySSH)
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

// the package for WeblogDriver that uses Kafka-Streams Processor APIs
lazy val procPackage = project
  .in(file("build/proc"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DeploySSH)
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
    mainClass in Compile := Some("com.lightbend.fdp.sample.kstream.processor.WeblogDriver")
  )
  .dependsOn(app)
