import sbt.Keys._
import sbt._

object DockerProjectSpecificPackagerPlugin extends AutoPlugin {

  import sbtdocker.DockerPlugin
  import DockerPlugin.autoImport._
  import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
  import JavaAppPackaging.autoImport._
  
  override def trigger  = allRequirements
  override def requires = DockerPlugin && JavaAppPackaging

  // base project settings
  def projectBase(id: String)(base: String = id) = Project(id, base = file(base))
    .settings(
      fork in run := true,
    )

  // settings for a native-packager based docker project based on sbt-docker plugin
  def sbtdockerPackagerBase(id: String, 
    applDir: TaskKey[sbt.File], 
    executableScriptName: SettingKey[String],
    dockerBaseImage: String = "openjdk:8u151-jre")(base: String = id) = projectBase(id)(base)

    .enablePlugins(sbtdocker.DockerPlugin)
    .settings(
      dockerfile in docker := {
        val targetDir = s"/$base"
  
        new Dockerfile {
          from(dockerBaseImage)
          entryPoint(s"$targetDir/bin/${executableScriptName}")
          copy(applDir.value, targetDir)
        }
      },
  
      // Set name for the image
      imageNames in docker := Seq(
        ImageName(namespace = Some(organization.value),
          repository = name.value.toLowerCase,
          tag = Some(version.value))
      ),
  
      buildOptions in docker := BuildOptions(cache = false)
    )
}
