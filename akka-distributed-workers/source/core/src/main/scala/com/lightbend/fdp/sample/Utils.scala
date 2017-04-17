package com.lightbend.fdp.sample

import scala.util.Random
import scala.annotation.tailrec

object Utils {
  def generateRandomListOfInt() = {
    val maxSizeOfList = 5
    val maxElementValue = 40

    List.fill(Random.nextInt(maxSizeOfList))(Random.nextInt(maxElementValue))
  }

  def factorial(number: Int) : BigInt = {
    @tailrec def factorialA(accumulator: BigInt, number: Int) : BigInt =
      if (number == 1) accumulator
      else factorialA(accumulator * number, number - 1)

    factorialA(1, number)
  }
}
