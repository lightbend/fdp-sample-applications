import sbt._
import Keys._

import sbtwhitesource.WhiteSourcePlugin.autoImport._

object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct               := "Fast Data Platform",
      whitesourceAggregateProjectName  := "fdp-killrweather",
      whitesourceAggregateProjectToken := "a7b3b3b6c303420db5644d321a8c8742bf49786fefe3431fa8cde0dc50493ab5"
    )
}
