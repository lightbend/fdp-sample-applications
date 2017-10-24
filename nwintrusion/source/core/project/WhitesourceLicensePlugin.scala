import sbt._
import Keys._

import sbtwhitesource.WhiteSourcePlugin.autoImport._

object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct               := "Fast Data Platform",
      whitesourceAggregateProjectName  := "fdp-sample-apps-nw-intrusion",
      whitesourceAggregateProjectToken := "90dba80f178e41daa8841b2cb6d050a48a8c218923ed468a80716d86bc95dd1c"
    )
}
