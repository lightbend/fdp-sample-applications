package com.lightbend.ad.model

import java.io.{DataInputStream, DataOutputStream}

import com.lightbend.ad.model.tensorflow.TensorFlowModel

import scala.util.Try

/**
  * Created by boris on 5/8/17.
  */
case class ModelWithDescriptor(model: Model, descriptor: ModelToServe)

object ModelWithDescriptor {

  def fromModelToServe(descriptor : ModelToServe): Try[ModelWithDescriptor] = Try{
    println(s"New model - $descriptor")
    ModelWithDescriptor(TensorFlowModel.create(descriptor),descriptor)
  }

  def readModel(input : DataInputStream) : (Option[Model]) = {
    val l = input.readLong()
    l match{
      case length if length > 0 => {
        val bytes = new Array[Byte](length.toInt)
        input.read(bytes)
        try {
          Some(TensorFlowModel.restore(bytes))
        }
        catch {
          case t: Throwable =>
            System.out.println("Error Deserializing model")
            t.printStackTrace()
            None
        }
      }
      case _ => None
    }
  }

  def writeModel(output : DataOutputStream, model: Model) : Unit = {
    if(model == null)
      output.writeLong(0l)
    else {
      try {
        val bytes = model.toBytes()
        val length = bytes.length.toLong
        output.writeLong(length)
        output.write(bytes)
      } catch {
        case t: Throwable =>
          System.out.println("Error Serializing model")
          t.printStackTrace()
      }
    }
  }

  def readModelWithDescriptor(input : DataInputStream) : (Option[ModelWithDescriptor]) = {
    val l = input.readLong()
    l match{
      case length if length > 0 => {
        val bytes = new Array[Byte](length.toInt)
        input.read(bytes)
        val name = input.readUTF()
        val description = input.readUTF()
        val dataType = input.readUTF()
        try {
          Some(ModelWithDescriptor(TensorFlowModel.restore(bytes), ModelToServe(name, description, dataType)))
        }
        catch {
          case t: Throwable =>
            System.out.println("Error Deserializing model")
            t.printStackTrace()
            None
        }
      }
      case _ => None
    }
  }


  def writeModel(output : DataOutputStream, model: ModelWithDescriptor) : Unit = {
    if(model == null)
      output.writeLong(0l)
    else {
      try {
        val bytes = model.model.toBytes()
        val length = bytes.length.toLong
        output.writeLong(length)
        output.write(bytes)
        output.writeUTF(model.descriptor.name)
        output.writeUTF(model.descriptor.description)
        output.writeUTF(model.descriptor.dataType)
      } catch {
        case t: Throwable =>
          System.out.println("Error Serializing model")
          t.printStackTrace()
      }
    }
  }
}
