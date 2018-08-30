package com.lightbend.ad.speculativemodelserver.processor

import com.lightbend.ad.model.ServingResult
import com.lightbend.ad.model.speculative.{Decider, ServingResponse}

object VotingDesider extends Decider {

  // The simple voting decider for results 0 or 1. Returning 0 or 1
  override def decideResult(results: List[ServingResponse]): Any = {

    var result = ServingResult.noModel
    var sum = .0
    var count = 0
    var duration = 0l
    var source = 0
    results.foreach(res => res.result match {
      case r if(r.processed) =>
        sum = sum + r.result.getOrElse(0)
        if(r.duration > duration) duration = r.duration
        if(r.source != source) source = r.source
        count = count + 1
      case _ =>
    })
    if(count == 0) result else {
      val res = sum/count
      val intres = if(res < .5) 0 else 1
      ServingResult("voter model", source, Some(intres), duration)
    }
  }
}
