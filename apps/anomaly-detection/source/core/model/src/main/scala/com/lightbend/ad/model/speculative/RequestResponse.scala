package com.lightbend.ad.model.speculative

import scala.util.{Random, Try}
import com.lightbend.ad.model.ServingResult
import com.lightbend.speculative.speculativedescriptor.SpeculativeDescriptor

// Because we are doing everything in memory, we implement local equivalent to protobufs

case class ServingRequest(GUID : String, data : Any)

case class ServingQualifier(key : String, value : String)

case class ServingResponse(GUID : String, result : ServingResult, confidence : Option[Double] = None, qualifiers : List[ServingQualifier] = List.empty)

object ServingResponse{

  val gen = Random
  val qualifiers = List(ServingQualifier("key", "value"))

  def apply(GUID: String,  result: ServingResult): ServingResponse = {
    new ServingResponse(GUID, result, Some(gen.nextDouble()), qualifiers)
  }
}

object SpeculativeConverter {
  def fromByteArray(message: Array[Byte]): Try[SpeculativeDescriptor] = Try {
    SpeculativeDescriptor.parseFrom(message)
  }
}