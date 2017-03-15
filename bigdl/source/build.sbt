import sbtassembly.MergeStrategy

val spark = "2.0.1"
lazy val commonSettings = Seq(
  version := "0.0.1",
  resolvers ++= Seq(
      Resolver.mavenLocal
    , "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
    , Resolver.sonatypeRepo("releases")
    , Resolver.sonatypeRepo("snapshots")
  ),
  scalaVersion := "2.11.8",
  unmanagedBase := baseDirectory.value / "lib",
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  libraryDependencies ++= Seq(
      "org.apache.spark"             %% "spark-core"                    % spark % "provided",
      "org.apache.spark"             %% "spark-mllib"                   % spark % "provided",
      "org.apache.spark"             %% "spark-sql"                     % spark % "provided",
      "org.rauschig"                  % "jarchivelib"                   % "0.7.1"
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
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.last
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
