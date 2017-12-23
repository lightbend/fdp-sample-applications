package com.lightbend.killrweater.beam.coders

import java.io._

import org.apache.beam.sdk.coders.Coder.NonDeterministicException
import org.apache.beam.sdk.coders.{AtomicCoder, CoderException}
import org.apache.beam.sdk.values.TypeDescriptor

class ScalaIntCoder extends AtomicCoder[Int] {

  import ScalaIntCoder._

  @throws[IOException]
  @throws[CoderException]
  override def encode(value: Int, outStream: OutputStream): Unit = {
    if (value == null) throw new CoderException("cannot encode a null Int")
    new DataOutputStream(outStream).writeInt(value)
  }

  @throws[IOException]
  @throws[CoderException]
  override def decode(inStream: InputStream): Int = try
    new DataInputStream(inStream).readInt
  catch {
    case ex@(_: EOFException | _: UTFDataFormatException) =>
      // These exceptions correspond to decoding problems, so change
      // what kind of exception they're branded as.
      throw new CoderException(ex)
  }

  @throws[NonDeterministicException]
  override def verifyDeterministic(): Unit = {}

  override def consistentWithEquals = true

  override def isRegisterByteSizeObserverCheap(value: Int) = true

  override def getEncodedTypeDescriptor: TypeDescriptor[Int] = TYPE_DESCRIPTOR

  @throws[Exception]
  override protected def getEncodedElementByteSize(value: Int): Long = {
    if (value == null) throw new CoderException("cannot encode a null Int")
    4
  }
}

object ScalaIntCoder{

  private val INSTANCE = new ScalaIntCoder()
  private val TYPE_DESCRIPTOR = new TypeDescriptor[Int]() {}

  def of: ScalaIntCoder = INSTANCE
}
