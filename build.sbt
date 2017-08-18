import deployssh.DeploySSH._

name := "KillrWeather"

version := "1.0"

scalaVersion in ThisBuild := "2.11.11"

lazy val protobufs = (project in file("./protobufs"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ))
  .settings(libraryDependencies ++= Dependencies.grpc)
  .settings(dependencyOverrides += "io.netty" % "netty-codec-http2" % "4.1.11.Final")
  .settings(dependencyOverrides += "io.netty" % "netty-handler-proxy" % "4.1.11.Final")

lazy val core = (project in file("./killrweather-core"))
  .settings(defaultSettings:_*)
  .settings(libraryDependencies ++= Dependencies.core)


lazy val app = (project in file("./killrweather-app"))
  .settings(defaultSettings:_*)
  .settings(
    mainClass in Compile := Some("com.lightbend.killrweather.app.KillrWeather"),
    maintainer := "Boris Lublinsky <boris.lublinsky@lightbend.com",
    packageSummary := "KillrWeather Spark Runner",
    packageDescription := "KillrWeather Spark Runner",
    libraryDependencies ++= Dependencies.app)
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7")
  .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
  .settings(dependencyDotFile := file("dependencies.dot"))
  .settings(
    maintainer := "Boris Lublinsky <boris.lublinsky@lightbend.com",
    packageSummary := "KillrWeather Spark uber jar",
    packageDescription := "KillrWeather Spark uber jar",
    assemblyJarName in assembly := "spark.jar",
    mainClass in assembly := Some("com.lightbend.killrweather.app.KillrWeather"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    deployResourceConfigFiles ++= Seq("deploy.conf"),
    deployArtifacts ++= Seq(
        ArtifactSSH(assembly.value, "/var/www/html/")
    )
  )
  .dependsOn(core, protobufs)
  .enablePlugins(DeploySSH)

lazy val appLocalRunner = (project in file("./killrweather-app-local"))
    .settings(
      libraryDependencies ++= Dependencies.spark.map(_.copy(configurations = Option("compile")))
    )
    .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core"  % "2.6.7")
    .settings(dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")
    .dependsOn(app)

lazy val clients = (project in file("./killrweather-clients"))
  .settings(defaultSettings:_*)
  .settings(
      buildInfoPackage := "build",
      mainClass in Compile := Some("com.lightbend.killrweather.client.http.RestAPIs"),
//      mainClass in Compile := Some("com.lightbend.killrweather.client.grpc.WeatherGRPCClient"),
      maintainer := "Boris Lublinsky <boris.lublinsky@lightbend.com",
      packageSummary := "KillrWeather HTTP client",
      packageDescription := "KillrWeather HTTP client",
      deployResourceConfigFiles ++= Seq("deploy.conf"),
      deployArtifacts ++= Seq(
          ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/")
      ),
      libraryDependencies ++= Dependencies.client)
  .dependsOn(core, protobufs)
  .enablePlugins(DeploySSH)
  .enablePlugins(JavaAppPackaging)

lazy val root = (project in file("."))
  .aggregate(core, app, clients, protobufs)

