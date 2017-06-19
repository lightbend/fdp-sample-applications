import sbt._
import Keys._
 
import sbtwhitesource.WhiteSourcePlugin.autoImport._
 
object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
 
  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct                := "Lightbend Reactive Platform",
      whitesourceAggregateProjectName   := "bigdlsample",
      whitesourceAggregateProjectToken  := "dc2e7c62-f5b4-4ced-9a19-b3234744bb19"
    )
}
