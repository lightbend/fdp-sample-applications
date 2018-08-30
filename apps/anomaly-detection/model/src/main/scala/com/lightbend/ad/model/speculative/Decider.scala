package com.lightbend.ad.model.speculative

trait Decider {

  def decideResult(results: List[ServingResponse]): Any
}
