import sbt._
import Keys._
 
import sbtwhitesource.WhiteSourcePlugin.autoImport._
 
object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
 
  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct                := "Lightbend Reactive Platform",
      whitesourceAggregateProjectName   := "fdp-nw-intrusion",
      whitesourceAggregateProjectToken  := "cd24ee06-2a0d-4754-8acc-43dada3378e9"
    )
}
