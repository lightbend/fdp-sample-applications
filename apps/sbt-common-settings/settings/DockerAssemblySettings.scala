import sbt.Keys._
import sbt._

object DockerProjectSpecificAssemblyPlugin extends AutoPlugin {

  import sbtdocker.DockerPlugin
  import DockerPlugin.autoImport._
  
  override def trigger  = allRequirements
  override def requires = DockerPlugin

  // base project settings
  def projectBase(id: String)(base: String = id) = Project(id, base = file(base))
    .settings(
      fork in run := true,
    )

  // settings for a native-packager based docker project based on sbt-docker plugin
  def sbtdockerAssemblySparkBase(id: String, 
    assembly: TaskKey[sbt.File],
    dockerSparkBaseImage: String = "lightbend/spark:2.3.1-2.2.1-2-hadoop-2.6.5-01",
    baseImageJarPath: String = "/opt/spark/dist/jars", 
    dockerSparkBaseImageForK8s: String = "lightbend/spark:2.0.0-OpenShift-2.4.0-ubuntu",
    baseImageForK8sJarPath: String = "/opt/spark/jars")(base: String = id) = projectBase(id)(base)

    .enablePlugins(sbtdocker.DockerPlugin)
    .settings(
      dockerfile in docker := {
        val artifact: File = assembly.value
        if (System.getProperty("K8S_OR_DCOS") == "K8S") {
          val artifactTargetPath = s"$baseImageForK8sJarPath/${artifact.name}"
  
          new Dockerfile {
            from (dockerSparkBaseImageForK8s)
            add(artifact, artifactTargetPath)
            runRaw("mkdir -p /etc/hadoop/conf")
            runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
          }
        } else {
          val artifactTargetPath = s"$baseImageJarPath/${artifact.name}"
  
          new Dockerfile {
            from (dockerSparkBaseImage)
            add(artifact, artifactTargetPath)
            runRaw("mkdir -p /etc/hadoop/conf")
            runRaw("export HADOOP_CONF_DIR=/etc/hadoop/conf")
          }
        }
      },
  
      // Set name for the image
      imageNames in docker := Seq(
        ImageName(namespace = Some(organization.value),
          repository = (if (System.getProperty("K8S_OR_DCOS") == "K8S") s"${name.value.toLowerCase}-k8s"
            else name.value.toLowerCase), 
          tag = Some(version.value))
      ),
  
      buildOptions in docker := BuildOptions(cache = false)
    )

  // settings for an assembly based docker project based on sbt-docker plugin
  def sbtdockerAssemblyFlinkBase(id: String,
    assembly: TaskKey[sbt.File])(base: String = id) = projectBase(id)(base)

    .enablePlugins(sbtdocker.DockerPlugin)
    .settings(
      dockerfile in docker := {
  
        val artifact: File = assembly.value
        val artifactTargetPath = s"/flink-1.4.2/app/jars/${artifact.name}"
  
        new Dockerfile {
          from ("mesosphere/dcos-flink:1.4.2-1.0")
          add(artifact, artifactTargetPath)
          runRaw("mkdir -p /flink-1.4.2/app/jars")
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

