package com.lightbend.ad.model

import java.io.ByteArrayOutputStream

import com.lightbend.model.modeldescriptor.{ModelDescriptor, ModelPreprocessing}

import scala.util.Try

/**
 * Created by boris on 5/8/17.
 */
object ModelToServe {
  def fromByteArray(message: Array[Byte]): Try[ModelToServe] = Try {
    val m = ModelDescriptor.parseFrom(message)
    m.messageContent.isData match {
      case true => new ModelToServe(m.name.replace(" ", "_"), m.description, m.dataType, m.getData.toByteArray,
        DataPreprocessor(m.getPreprocessing.width, m.getPreprocessing.mean, m.getPreprocessing.std, m.getPreprocessing.input, m.getPreprocessing.output))
      case _ => throw new Exception("Location based is not yet supported")
    }
  }
}

case class ModelToServe(name: String, description: String, dataType: String,
  model: Array[Byte] = Array.empty, preprocessor : DataPreprocessor = null)

case class DataPreprocessor(width: Int, mean : Double, std : Double, input : String, output : String)

object DataPreprocessor{
  val bos = new ByteArrayOutputStream()

  def toByteArray(p : DataPreprocessor) : Array[Byte] = {
    val pb = new ModelPreprocessing(p.width, p.mean, p.std, p.input, p.output)
    bos.reset()
    pb.writeTo(bos)
    bos.toByteArray
  }
  def fromByteArray(bytes : Array[Byte]) : DataPreprocessor = {
    val pb = ModelPreprocessing.parseFrom(bytes)
    DataPreprocessor(pb.width, pb.mean, pb.std, pb.input, pb.output)
  }
}


case class ServingResult(processed : Boolean, model : String = "", source : Int=0, result: Option[Int]=None, duration: Long = 0l)

object ServingResult{
  def noModel = ServingResult(false)
  def apply(model : String, source : Int, result: Option[Int], duration: Long): ServingResult = ServingResult(true, model, source, result, duration)
}