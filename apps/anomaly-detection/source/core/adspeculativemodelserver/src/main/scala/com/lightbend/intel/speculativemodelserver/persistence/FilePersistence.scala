package com.lightbend.ad.speculativemodelserver.persistence

import java.io._

import com.lightbend.ad.model.{ModelToServeStats, ModelWithDescriptor}

object FilePersistence {

  private final val baseDir = "persistence"
  private final val modelFilter = new FilenameFilter {
    override def accept(dir: File, name: String): Boolean = name.startsWith("model_")
  }

  def restoreModelState(model: String): (Option[ModelWithDescriptor]) = {
    getDataInputStream(s"model_$model") match {
      case Some(input) => (ModelWithDescriptor.readModelWithDescriptor(input))
      case _ => None
    }
  }

  def restoreDataState(dataType: String): Option[Long] = {
    getDataInputStream(s"data_$dataType") match {
      case Some(input) => {
        Some(input.readLong)
      }
      case _ => None
    }
  }

  private def getDataInputStream(fileName: String): Option[DataInputStream] = {
    val file = new File(baseDir + "/" + fileName)
    file.exists() match {
      case true => Some(new DataInputStream(new FileInputStream(file)))
      case _ => None
    }
  }

  def saveModelState(modelID: String, model: ModelWithDescriptor): Unit = {
    val output = getDataOutputStream(s"model_$modelID")
    ModelWithDescriptor.writeModel(output, model)
    output.flush()
    output.close()
  }

  def saveDataState(dataType: String, timeout: Long): Unit = {
    val output = getDataOutputStream(s"data_$dataType")
    output.writeLong(timeout)
    output.flush()
    output.close()
  }

  private def getDataOutputStream(fileName: String): DataOutputStream = {

    val dir = new File(baseDir)
    if (!dir.exists()) {
      dir.mkdir()
    }
    val file = new File(dir, fileName)
    if (!file.exists())
      file.createNewFile()
    new DataOutputStream(new FileOutputStream(file))
  }

  def getKnownModels(): Seq[String] = {
    val dir = new File(baseDir)
    if (!dir.exists()) Seq.empty
    else dir.listFiles(modelFilter).toSeq.map(_.getName)
  }
}