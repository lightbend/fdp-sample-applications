package com.lightbend.kafka

import org.scalatest.{Matchers, WordSpec}

class WineRecordOpsTest extends WordSpec with Matchers {

  "toWineRecord" should {
    "create a WineRecord from CSV values in a String" in {

      // sample line from data file "7.4;0.7;0;1.9;0.076;11;34;0.9978;3.51;0.56;9.4;5"

      val sampleData = (7.4, 0.7, 0.0, 1.9, 0.076, 11.0, 34.0, 0.9978, 3.51, 0.56, 9.4)
      val (fixedAcidity, volatileAcidity, citricAcid, residualSugar, chlorides, freeSulfurDioxide,
            totalSulfurDioxide, density, pH, sulphates, alcohol) = sampleData
      
      val sample = sampleData.productIterator.mkString(";")
      val wineRecord = WineRecordOps.toWineRecord(sample)
      wineRecord.dataType should be ("wine")
      wineRecord.fixedAcidity should be (fixedAcidity)
      wineRecord.volatileAcidity should be (volatileAcidity)
      wineRecord.citricAcid should be (citricAcid)
      wineRecord.residualSugar should be (residualSugar)
      wineRecord.chlorides should be (chlorides)
      wineRecord.freeSulfurDioxide should be (freeSulfurDioxide)
      wineRecord.totalSulfurDioxide should be (totalSulfurDioxide)
      wineRecord.density should be (density)
      wineRecord.pH should be (pH)
      wineRecord.sulphates should be (sulphates)
      wineRecord.alcohol should be (alcohol)
    }

  }

}
