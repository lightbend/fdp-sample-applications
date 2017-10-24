import sbt._
import Keys._

import sbtwhitesource.WhiteSourcePlugin.autoImport._

object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct               := "Fast Data Platform",
      whitesourceAggregateProjectName  := "fdp-sample-apps-kstream",
      whitesourceAggregateProjectToken := "d2cb3f22a94f42f78f50d7beddfa547e8a664026babb4452aca516817230569d"
    )
}
