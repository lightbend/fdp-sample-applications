import sbt._
import Keys._

import sbtwhitesource.WhiteSourcePlugin.autoImport._

object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct               := "Fast Data Platform",
      whitesourceAggregateProjectName  := "fdp-sample-apps-bigdl",
      whitesourceAggregateProjectToken := "08c229e337e74c63be5def38720d454017fdfac04e6841418bd90d2885309fe8"
    )
}
