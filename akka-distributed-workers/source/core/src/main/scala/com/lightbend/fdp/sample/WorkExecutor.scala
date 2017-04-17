package com.lightbend.fdp.sample

import akka.actor.Actor

class WorkExecutor extends Actor {

  def receive = {
    case es: List[Int] => sender() ! Worker.WorkComplete(es.map(Utils.factorial(_)).sum)
  }

}
