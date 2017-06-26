import sbt._
import Keys._
 
import sbtwhitesource.WhiteSourcePlugin.autoImport._
 
object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
 
  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct                := "Lightbend Reactive Platform",
      whitesourceAggregateProjectName   := "fdp-kstream",
      whitesourceAggregateProjectToken  := "92a161b2-b579-4a3e-9288-288773b96349"
    )
}

