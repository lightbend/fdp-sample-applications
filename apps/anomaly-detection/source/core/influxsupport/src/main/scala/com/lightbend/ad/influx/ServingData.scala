package com.lightbend.ad.influx

case class ServingData(served : Long, source : Long, model : String, duration : Long)
case class ServingModelData(served : Long, model : String, duration : Long)
