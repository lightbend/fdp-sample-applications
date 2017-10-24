import sbt._
import Keys._

import sbtwhitesource.WhiteSourcePlugin.autoImport._

object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct               := "Fast Data Platform",
      whitesourceAggregateProjectName  := "fdp-akka-kafka-streams-model-server",
      whitesourceAggregateProjectToken := "73a3eff56f904537a33c696cc9dbbd697fa6f9b18e7a413daadf7201f2db2f76"
    )
}
