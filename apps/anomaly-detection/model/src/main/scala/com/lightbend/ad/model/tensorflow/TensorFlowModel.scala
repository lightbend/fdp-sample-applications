package com.lightbend.ad.model.tensorflow

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import com.lightbend.ad.model.{DataPreprocessor, Model, ModelFactory, ModelToServe}
import com.lightbend.model.cpudata.CPUData
import org.tensorflow.{Graph, Session, Tensor}


/**
 * Created by boris on 5/26/17.
 * Implementation of a model using TensorFlow for "Records".
 */
class TensorFlowModel(inputStream: Array[Byte], pd : DataPreprocessor) extends Model {

  val preprocessor = new Preprocessor(pd.width, pd.mean, pd.std)
  val graph = new Graph
  graph.importGraphDef(inputStream)
  val session = new Session(graph)

  override def score(data: CPUData): Option[Int] = {

    preprocessor.addMeasurement(data.utilization)
    if(preprocessor.currentWidth >= pd.width) {
      val tmatrix: Array[Array[Float]] = Array(preprocessor.getValue())
      val input = Tensor.create(tmatrix)
      val result = session.runner.feed(pd.input, input).fetch(pd.output).run.get(0)
      val rshape = result.shape
      val rArray: Array[Array[Float]] = Array.ofDim(rshape(0).asInstanceOf[Int], rshape(1).asInstanceOf[Int])
      val y = result.copyTo(rArray)
      val softmax = rArray(0)
      if (softmax(0) >= softmax(1)) Some(0) else Some(1)
    }
    else None
  }

  override def cleanup(): Unit = {
    try {
      session.close
    } catch {
      case t: Throwable => // Swallow
    }
    try {
      graph.close
    } catch {
      case t: Throwable => // Swallow
    }
  }

  override def toBytes(): Array[Byte] = {
    val p = DataPreprocessor.toByteArray(pd)
    val g = graph.toGraphDef
    val bos = new ByteArrayOutputStream()
    val dos = new DataOutputStream(bos)
    dos.writeLong(p.length.toLong)
    dos.write(p)
    dos.writeLong(g.length.toLong)
    dos.write(g)
    bos.toByteArray
  }
}

object TensorFlowModel extends ModelFactory {
  def apply(inputStream: Array[Byte], pd : DataPreprocessor): Option[TensorFlowModel] = {
    try {
      Some(new TensorFlowModel(inputStream, pd))
    } catch {
      case t: Throwable => None
    }
  }

  override def create(input: ModelToServe): Model = {
    new TensorFlowModel(input.model, input.preprocessor)
  }

  override def restore(bytes: Array[Byte]): Model = {
    val dis = new DataInputStream(new ByteArrayInputStream(bytes))
    val plen = dis.readLong().toInt
    val p = new Array[Byte](plen)
    dis.read(p)
    val preprocessor = DataPreprocessor.fromByteArray(p)
    val glen = dis.readLong().toInt
    val g = new Array[Byte](glen)
    dis.read(g)
    new TensorFlowModel(g, preprocessor)
  }
}

class Preprocessor(width : Int, mean: Double, std: Double){
  var currentWidth = 0
  val value : Array[Float] = new Array[Float](width)

  def getCurrentWidth() : Int = currentWidth

  def addMeasurement(v : Double) : Unit = {

    1 to width -1  foreach(i => value(i-1) = value(i))
    value(width -1) = standardize(v)
    if (currentWidth < width)
      currentWidth = currentWidth + 1
  }
  def getValue() : Array[Float] = value

  def standardize(value: Double): Float = {
    ((value - mean) / std).toFloat
  }
}
