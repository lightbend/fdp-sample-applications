/*
 * Licensed to Intel Corporation under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * Intel Corporation licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lightbend.fdp.sample.bigdl

import java.nio.file.{ Files, Paths }

import com.intel.analytics.bigdl.dataset.DataSet
import com.intel.analytics.bigdl.dataset.image._
import com.intel.analytics.bigdl.nn.{ClassNLLCriterion, Module}
import com.intel.analytics.bigdl._
import com.intel.analytics.bigdl.optim._
import com.intel.analytics.bigdl.utils.{Engine, T}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkContext
import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric._
import com.intel.analytics.bigdl.models.vgg._

import scala.util.{ Try, Success, Failure }

object TrainVGG {
  Logger.getLogger("org").setLevel(Level.ERROR)
  Logger.getLogger("akka").setLevel(Level.ERROR)
  Logger.getLogger("breeze").setLevel(Level.ERROR)
  Logger.getLogger("com.intel.analytics.bigdl.optim").setLevel(Level.INFO)

  import Utils._

  import sys.process._
  import java.net.URL
  import java.io.File
  import scala.language.postfixOps
  import org.rauschig.jarchivelib._
  import scala.util.{ Try, Success, Failure }

  def download(downloadRoot: String): Try[Unit] = Try {
    val cifarDataUri = "https://www.cs.toronto.edu/~kriz/cifar-10-binary.tar.gz"

    println(s"cifarDataURI = $cifarDataUri")

    val _ = (new URL(cifarDataUri) #> new File(s"$downloadRoot/cifar-10-binary.tar.gz") !!)
    ()
  }

  def unarchive(gzippedArchiveName: String, downloadRootFolder: String): Try[Unit] = Try {
    val archive = new File(gzippedArchiveName)
    val destination = new File(s"$downloadRootFolder/")

    val archiver = ArchiverFactory.createArchiver("tar", "gz")
    archiver.extract(archive, destination)
    ()
  }

  def extractCifarData(downloadRootFolder: String): Try[Unit] = for {
    _ <- download(downloadRootFolder)
    _ <- unarchive(s"$downloadRootFolder/cifar-10-binary.tar.gz", downloadRootFolder)
  } yield ()

  def main(args: Array[String]): Unit = {

    val _ = trainParser.parse(args, new TrainParams()).map(param => {

      if (Files.notExists(Paths.get(param.folder))) {
        println(s"cifar-10 data does not exist .. going to download")
        extractCifarData(param.downloadRootFolder) match {
          case Success(_) => ()
          case Failure(ex) => throw ex
        }
      }
  
      if (Files.notExists(Paths.get(param.folder))) {
        throw new Exception("CIFAR data has not been downloaded")
      }

      val conf = param.master.map { m => 
        Engine.createSparkConf().setAppName("vggtrainapp").setMaster(m).set("spark.rpc.message.maxSize", "200")
      }.getOrElse {
        Engine.createSparkConf().setAppName("vggtrainapp").set("spark.rpc.message.maxSize", "200")
      }
      val sc = new SparkContext(conf)
      Engine.init

      val trainDataSet = DataSet.array(Utils.loadTrain(param.folder), sc) ->
        BytesToBGRImg() -> BGRImgNormalizer(trainMean, trainStd) ->
        BGRImgToBatch(param.batchSize)

      val model = param.modelSnapshot.map {
        Module.loadModule[Float](_)
      }.getOrElse {
        VggForCifar10(classNum = 10)
      }

      val optimMethod = if (param.stateSnapshot.isDefined) {
        OptimMethod.load[Float](param.stateSnapshot.get)
      } else {
        new SGD[Float](learningRate = param.learningRate, learningRateDecay = 0.0,
          weightDecay = param.weightDecay, momentum = 0.9, dampening = 0.0, nesterov = false,
          learningRateSchedule = SGD.EpochStep(25, 0.5))
      }

      val optimizer = Optimizer(
        model = model,
        dataset = trainDataSet,
        criterion = new ClassNLLCriterion[Float]()
      )

      val validateSet = DataSet.array(Utils.loadTest(param.folder), sc) ->
        BytesToBGRImg() -> BGRImgNormalizer(testMean, testStd) ->
        BGRImgToBatch(param.batchSize)

      if (param.checkpoint.isDefined) {
        optimizer.setCheckpoint(param.checkpoint.get, Trigger.everyEpoch)
      }

      if(param.overWriteCheckpoint) {
        optimizer.overWriteCheckpoint()
      }

      optimizer
        .setValidation(Trigger.everyEpoch, validateSet, Array(new Top1Accuracy[Float]))
        .setOptimMethod(optimMethod)
        .setEndWhen(Trigger.maxEpoch(param.maxEpoch))
        .optimize()

      sc.stop()
    })
    ()
  }
}


