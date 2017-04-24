package com.lightbend.fdp.sample

import scala.concurrent.duration._

object MasterWorkerProtocol {
  // Messages from Workers
  case class RegisterWorker(workerId: String, registerInterval: FiniteDuration)
  case class WorkerRequestsWork(workerId: String)
  case class WorkIsDone(workerId: String, workId: String, result: Any)
  case class WorkFailed(workerId: String, workId: String)

  // Messages to Workers
  case object WorkIsReady
  case class Ack(id: String)
}
