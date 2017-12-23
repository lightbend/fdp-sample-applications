package com.lightbend.killrweater.beam.coders

import java.io._
import java.nio.charset.StandardCharsets

import com.google.common.base.Utf8
import org.apache.beam.sdk.coders.{AtomicCoder, CoderException}
import org.apache.beam.sdk.util.VarInt
import org.apache.beam.sdk.values.TypeDescriptor

class ScalaStringCoder extends AtomicCoder[String] {

  import ScalaStringCoder._

  @throws[IOException]
  override def encode(value: String, outStream: OutputStream): Unit = {
    writeString(value, new DataOutputStream(outStream))
  }

  @throws[IOException]
  override def decode(inStream: InputStream): String = {
    try
      readString(new DataInputStream(inStream))
    catch {
      case exn@(_: EOFException | _: UTFDataFormatException) =>
        // These exceptions correspond to decoding problems, so change
        // what kind of exception they're branded as.
        throw new CoderException(exn)
    }
  }

  override def verifyDeterministic(): Unit = {}

  override def consistentWithEquals = true

  override def isRegisterByteSizeObserverCheap(value: String) = true

  override def getEncodedTypeDescriptor: TypeDescriptor[String] = TYPE_DESCRIPTOR

  @throws[Exception]
  override def getEncodedElementByteSize(value: String) : Long = {
    if (value == null) throw new CoderException("cannot encode a null String")
    val size = Utf8.encodedLength(value)
    return VarInt.getLength(size) + size
  }
}

object ScalaStringCoder {

  private val INSTANCE = new ScalaStringCoder()
  private val TYPE_DESCRIPTOR = new TypeDescriptor[String]() {}

  def of: ScalaStringCoder = INSTANCE

  @throws[IOException]
  def writeString(value: String, dos: DataOutputStream): Unit = {
    val bytes = value.getBytes(StandardCharsets.UTF_8)
    VarInt.encode(bytes.length, dos)
    dos.write(bytes)
  }

  @throws[IOException]
  def readString(dis: DataInputStream) = {
    val len = VarInt.decodeInt(dis)
    if (len < 0) throw new CoderException("Invalid encoded string length: " + len)
    val bytes = new Array[Byte](len)
    dis.readFully(bytes)
    new String(bytes, StandardCharsets.UTF_8)
  }
}