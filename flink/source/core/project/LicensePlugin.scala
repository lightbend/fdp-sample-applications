import sbt._
import Keys._
 
import sbtwhitesource.WhiteSourcePlugin.autoImport._
 
object LicensePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
 
  override def buildSettings: Seq[Setting[_]] =
    List(
      whitesourceProduct                := "Lightbend Reactive Platform",
      whitesourceAggregateProjectName   := "fdp-flink-taxiride",
      whitesourceAggregateProjectToken  := "049d42ba-8186-4542-8fa4-988a04c690da"
    )
}

