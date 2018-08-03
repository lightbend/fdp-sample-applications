package com.lightbend.killrweater.beam.coders

import java.io._

import org.apache.beam.sdk.coders.Coder.NonDeterministicException
import org.apache.beam.sdk.coders.{AtomicCoder, CoderException}
import org.apache.beam.sdk.values.TypeDescriptor

class ScalaDoubleCoder extends AtomicCoder[Double] {

  import ScalaDoubleCoder._

  @throws[IOException]
  @throws[CoderException]
  override def encode(value: Double, outStream: OutputStream): Unit = {
    new DataOutputStream(outStream).writeDouble(value)
  }

  @throws[IOException]
  @throws[CoderException]
  override def decode(inStream: InputStream): Double = try
    new DataInputStream(inStream).readDouble
  catch {
    case exn@(_: EOFException | _: UTFDataFormatException) =>
      // These exceptions correspond to decoding problems, so change
      // what kind of exception they're branded as.
      throw new CoderException(exn)
  }

  @throws[NonDeterministicException]
  override def verifyDeterministic(): Unit = {
    throw new NonDeterministicException(this, "Floating point encodings are not guaranteed to be deterministic.")
  }

  override def consistentWithEquals = true

  override def isRegisterByteSizeObserverCheap(value: Double) = true

  override def getEncodedTypeDescriptor: TypeDescriptor[Double] = TYPE_DESCRIPTOR

  @throws[Exception]
  override protected def getEncodedElementByteSize(value: Double): Long = 8

}

object ScalaDoubleCoder{

  private val INSTANCE = new ScalaDoubleCoder()
  private val TYPE_DESCRIPTOR = new TypeDescriptor[Double]() {}

  def of: ScalaDoubleCoder = INSTANCE
}