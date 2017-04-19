package com.lightbend.fdp.sample

import scala.util.Random
import scala.annotation.tailrec

object Utils {
  def generateRandomListOfInt(maxListSize: Int, maxElementValue: Int) =
    List.fill(Random.nextInt(maxListSize))(Random.nextInt(maxElementValue))

  def factorial(number: Int) : BigInt = {
    @tailrec def factorialA(accumulator: BigInt, number: Int) : BigInt =
      if (number == 0) 1
      else if (number == 1) accumulator
      else factorialA(accumulator * number, number - 1)

    factorialA(1, number)
  }
}
