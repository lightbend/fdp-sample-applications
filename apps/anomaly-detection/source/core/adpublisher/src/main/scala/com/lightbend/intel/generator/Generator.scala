package com.lightbend.ad.generator

import scala.util.Random


trait Generator{
  def next() : Double
}

/* Define a noise model out of a random generator.
 * If the model type is "normal", expected parameters are mean and stddev.
 * Default is uniform with no parameters.
 */

class Noise(distribution : String, mean : Double = 0, stdev : Double = 0) extends Generator {
  val generator = new Random()

  override def next(): Double =
    distribution match {
      case "normal" => generator.nextGaussian() * stdev + mean
      case _ => generator.nextDouble()
    }
}

/* Simple deterministic step function
 */

class Step(setT : Double, initT : Double = 0.0, dt : Double = 1.0) extends Generator {
  var current = initT

  override def next(): Double = {
    val output = if(current < setT) 0.0 else 1.0
    current = current + dt
    output
  }
}

/* Simple deterministic ramp function
 */

class Ramp(setT : Double, duration : Double, slope : Double = 1, initT : Double = 0, dt : Double = 1) extends Generator {
  var current = initT
  val reset = setT + duration
  override def next(): Double = {
    val output = if(current < setT || (current > reset)) 0.0 else slope
    current = current + dt
    output
  }
}

/* Pulse
 */

class Pulse(setT : Double, duration : Double, initT : Double = 0, dt : Double = 1) extends Generator {
  var current = initT
  val reset = setT + duration

  override def next(): Double = {
    val output = if (current < setT) 0.0 else if (current < reset) 1.0 else -1.0
    current = current + dt
    output
  }
}

/* Random Pulses
 */

class RandomPulses(meanOnset : Double, meanDuty : Double, initT : Double =0, dt: Double =1) extends Generator {
  val generator = new Random()
  var pulse = newPulse()

  def newPulse() : Pulse = new Pulse(generator.nextGaussian() + meanOnset, generator.nextGaussian() + meanDuty, initT, dt)

  override def next(): Double = {
    val result = pulse.next()
    if (result < 0){
      pulse = newPulse()
      pulse.next()
    }
    else
      result
  }
}

// Sinusoid
class Sinusoid(period : Double, delay : Double = 0, scale : Double = 1.0, initT : Double = 0, dt : Double = 1) extends Generator {
  var current = initT

  override def next(): Double = {
    val output = scale * Math.sin(Math.toRadians(current / period - delay))
    current = current + dt
    output
  }
}

class Sinusoids(periods : Seq[Double], delays : Seq[Double], weights : Seq[Double], initT :Double = 0, dt : Double = 1) extends Generator {
  var current = initT
  val params = (weights,periods,delays).zipped.toSeq

  override def next(): Double = {
    val output = params.map(param => param._1 * Math.sin(Math.toRadians(current / param._2 - param._3))).foldLeft(0.0)(_ + _)
    current = current + dt
    output
  }
}

/* Additive and multiplicative models are the most common ways
   to generate complex metrics out of simple waveforms
*/
class Model(models : Seq[(Generator, Double)], operator : String = "add") extends Generator {

  override def next(): Double = {

    if (operator == "add")
      models.foldLeft(0.0)((r, c) => r + c._2 * c._1.next())
    else
      models.foldLeft(1.0)((r, c) => r * c._2 * c._1.next())
  }
}

  /* Markov Chain for generating anomalous data
   *  STM: State Transition Matrix, Array of Arrays of floats - probabilty of the state transitions
   */

class MarkovChain(STM : Array[Array[Double]], modelseq: Seq[Generator] = Seq.empty, delay : Int = 1, initState : Int = 0)  extends Generator {

  val generator = new Random()
  var state = initState
  var models = modelseq
  var nochange = delay
  var labels : Seq[Int] = Seq(initState)
  val cummulativeSTM = STM.map(row => {
    var sum = .0
    row.map(v=> {
      val nv = v+sum
      sum = nv
      nv
    })
  })

  override def next(): Double = {

    nochange = nochange - 1
    if(nochange <= 0) {
      // state transition
      val probability = generator.nextDouble()
      val transition = cummulativeSTM(state).map(v => if ((probability - v) > 0) 1 else 0).foldLeft(0)(_ + _)
      if (transition == 1)
        state = 1 - state
      nochange = delay
    }
    labels = labels :+ state
    models(state).next()
  }

  def getLabel() : Int = labels.last

  // Get labels
  def getLabels(): Seq[Int] = labels
}

