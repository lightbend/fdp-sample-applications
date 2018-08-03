package com.lightbend.killrweater.beam.data

/**
  * A class for tracking the statistics of a set of numbers (count, mean and variance) in a
  * numerically robust way. Includes support for merging two StatCounters. Based on Welford
  * and Chan's <a href="http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance">
  * algorithms</a> for running variance.
  *
  * Stollen from Spark
  */

class StatCounter(values: TraversableOnce[Double]) {
  private var n: Long = 0     // Running count of our values
  private var mu: Double = 0  // Running mean of our values
  private var m2: Double = 0  // Running variance numerator (sum of (x - mean)^2)
  private var maxValue: Double = Double.NegativeInfinity // Running max of our values
  private var minValue: Double = Double.PositiveInfinity // Running min of our values

  merge(values)

  /** Initialize the StatCounter with no values. */
  def this() = this(Nil)

  /** Add a value into this StatCounter, updating the internal statistics. */
  def merge(value: Double): StatCounter = {
    val delta = value - mu
    n += 1
    mu += delta / n
    m2 += delta * (value - mu)
    maxValue = math.max(maxValue, value)
    minValue = math.min(minValue, value)
    this
  }

  /** Add multiple values into this StatCounter, updating the internal statistics. */
  def merge(values: TraversableOnce[Double]): StatCounter = {
    values.foreach(v => merge(v))
    this
  }

  def count: Long = n

  def mean: Double = mu

  def sum: Double = n * mu

  def max: Double = maxValue

  def min: Double = minValue

  /** Return the population variance of the values. */
  def variance: Double = popVariance

  /**
    * Return the population variance of the values.
    */
  def popVariance: Double = {
    if (n == 0) {
      Double.NaN
    } else {
      m2 / n
    }
  }

  /**
    * Return the sample variance, which corrects for bias in estimating the variance by dividing
    * by N-1 instead of N.
    */
  def sampleVariance: Double = {
    if (n <= 1) {
      Double.NaN
    } else {
      m2 / (n - 1)
    }
  }

  /** Return the population standard deviation of the values. */
  def stdev: Double = popStdev

  /**
    * Return the population standard deviation of the values.
    */
  def popStdev: Double = math.sqrt(popVariance)

  /**
    * Return the sample standard deviation of the values, which corrects for bias in estimating the
    * variance by dividing by N-1 instead of N.
    */
  def sampleStdev: Double = math.sqrt(sampleVariance)

  override def toString: String = {
    "(count: %d, mean: %f, stdev: %f, max: %f, min: %f)".format(count, mean, stdev, max, min)
  }
}

object StatCounter {
  /** Build a StatCounter from a list of values. */
  def apply(values: TraversableOnce[Double]): StatCounter = new StatCounter(values)

  /** Build a StatCounter from a list of values passed as variable-length arguments. */
  def apply(values: Double*): StatCounter = new StatCounter(values)
}