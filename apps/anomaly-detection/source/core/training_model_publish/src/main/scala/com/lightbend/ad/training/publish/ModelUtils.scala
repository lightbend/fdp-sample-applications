package com.lightbend.ad.training.publish

import java.io._
import java.nio.file.{Files, Paths}
import collection.JavaConverters._

import org.apache.kafka.clients.producer.RecordMetadata
import com.google.protobuf.ByteString

import cats._
import cats.implicits._
import cats.effect.IO

import com.lightbend.model.modeldescriptor.{ModelDescriptor, ModelPreprocessing}
import com.lightbend.ad.kafka._

object ModelUtils {

  def readModel(modelFilePath: String, attribFilePath: String, hyperparamsFileName: String): IO[ModelDescriptor] = IO {
    // read model
    val pByteArray = Files.readAllBytes(Paths.get(modelFilePath))

    // read attributes
    val props: Map[String, String] = parseProperties(attribFilePath)

    val preprocessing = ModelPreprocessing(
      width = props("width").toInt,
      mean = props("mean").toFloat,
      std = props("std").toFloat,
      input = props("input"),
      output = props("output")
    )

    // read hyperparams
    val hyperparams: Map[String, String] = parseProperties(hyperparamsFileName)

    // if hyperparam file contains the following
    //
    // [HyperparameterSection]
    // learning_rate = 0.001
    // training_epochs = 12
    // batch_size = 256
    //
    // then the generated suffix will be 0.00112256
    val hyperparamSuffix = hyperparams.values.toList.foldMap(identity)

    ModelDescriptor(
      name = s"CPU_Anomaly_model_$hyperparamSuffix",
      description = s"""CPU Anomaly model generated at ${props("generatedAt")}""",
      dataType = "cpu",
      preprocessing = Some(preprocessing)
    ).withData(ByteString.copyFrom(pByteArray))
  }

  private def parseProperties(filePath: String): Map[String, String] = {
    val lines = Files.readAllLines(Paths.get(filePath)).asScala.toList
    val cleanLines = lines.map(_.trim).filter(!_.startsWith("#")).filter(_.contains("="))
    cleanLines.map(line => { val Array(a,b) = line.split("=",2); (a.trim, b.trim)}).toMap
  }

  def publishModelToKafka(sender: KafkaMessageSender,  model: ModelDescriptor, modelTopic: String): IO[RecordMetadata] = IO {
    println(s"Publishing model with descriptor $model")
    val bos = new ByteArrayOutputStream()
    model.writeTo(bos)
    sender.writeValue(modelTopic, bos.toByteArray)
  }
}
