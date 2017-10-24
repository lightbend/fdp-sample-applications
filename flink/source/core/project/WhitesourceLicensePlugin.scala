import sbt._
import Keys._

import sbtwhitesource.WhiteSourcePlugin.autoImport._

object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct               := "Fast Data Platform",
      whitesourceAggregateProjectName  := "fdp-sample-apps-flink-taxi-rides",
      whitesourceAggregateProjectToken := "d41c94d3c1854b51a8428bd8dfa89d518c0f77a6652b4ec9910914386baba5ba"
    )
}
