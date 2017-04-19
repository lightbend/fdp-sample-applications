package com.lightbend.fdp.sample

import java.util.UUID
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef

object WorkProducer {
  case object Tick
}

class WorkProducer(frontend: ActorRef, maxListSize: Int, maxElementValue: Int) extends Actor with ActorLogging {
  import WorkProducer._
  import context.dispatcher
  def scheduler = context.system.scheduler
  def rnd = ThreadLocalRandom.current
  def nextWorkId(): String = UUID.randomUUID().toString

  override def preStart(): Unit =
    scheduler.scheduleOnce(2.seconds, self, Tick)

  // override postRestart so we don't call preStart and schedule a new Tick
  override def postRestart(reason: Throwable): Unit = ()

  def receive = {
    case Tick =>
      val ls = Utils.generateRandomListOfInt(maxListSize, maxElementValue)
      log.info("Produced work: {}", ls)
      val work = Work(nextWorkId(), ls)
      frontend ! work
      context.become(waitAccepted(work), discardOld = false)

  }

  def waitAccepted(work: Work): Actor.Receive = {
    case Frontend.Ok =>
      context.unbecome()
      scheduler.scheduleOnce(rnd.nextInt(3, 10).seconds, self, Tick)
    case Frontend.NotOk =>
      log.info("Work not accepted, retry after a while")
      scheduler.scheduleOnce(3.seconds, frontend, work)
  }

}
