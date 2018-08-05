package com.lightbend.modelServer.modelServer

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.kafka.{ ConsumerSettings, Subscriptions }
import akka.kafka.scaladsl.Consumer
import akka.stream.{ ActorMaterializer, SourceShape }
import akka.stream.scaladsl.{ GraphDSL, Sink, Source }
import akka.util.Timeout

import scala.concurrent.duration._
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelServer.ModelToServe
import com.lightbend.modelServer.model.DataRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import akka.http.scaladsl.Http
import com.lightbend.configuration.{ AppConfig, AppParameters }

import com.lightbend.modelServer.queriablestate.QueriesAkkaHttpResource

object AkkaModelServer {

  implicit val system = ActorSystem("ModelServing")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  import AppConfig._
  import AppParameters._

  println(s"Akka Streams model config: ${AppConfig.stringify()}")

  def consumerSettings(group: String) = {
    ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(KAFKA_BROKER)
      .withGroupId(group)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
  }

  val dataConsumerSettings = consumerSettings(DATA_GROUP)
  val modelConsumerSettings = consumerSettings(MODELS_GROUP)

  def main(args: Array[String]): Unit = {

    val modelStream: Source[ModelToServe, Consumer.Control] =
      Consumer.atMostOnceSource(modelConsumerSettings, Subscriptions.topics(MODELS_TOPIC))
        .map(record => ModelToServe.fromByteArray(record.value())).filter(x => x.isSuccess).map(_.get)

    val dataStream: Source[WineRecord, Consumer.Control] =
      Consumer.atMostOnceSource(dataConsumerSettings, Subscriptions.topics(DATA_TOPIC))
        .map(record => DataRecord.fromByteArray(record.value())).filter(_.isSuccess).map(_.get)

    val model = new ModelStage(INFLUX_DB_CONFIG, GRAFANA_CONFIG)

    def keepModelMaterializedValue[M1, M2, M3](m1: M1, m2: M2, m3: M3): M3 = m3

    val modelPredictions: Source[Option[Double], ReadableModelStateStore] = Source.fromGraph(
      GraphDSL.create(dataStream, modelStream, model)(keepModelMaterializedValue) { implicit builder => (d, m, stage) =>
        import GraphDSL.Implicits._

        // wire together the input streams with the model stage (2 in, 1 out)
        /*
                            dataStream --> |       |
                                           | model | -> predictions
                            modelStream -> |       |
          */

        d ~> stage.in0
        m ~> stage.in1
        SourceShape(stage.out)
      }
    )

    val materializedReadableModelStateStore: ReadableModelStateStore =
      modelPredictions
        //        .map(println(_))
        .to(Sink.ignore) // we do not read the results directly
        .run() // we run the stream, materializing the stage's StateStore

    startRest(materializedReadableModelStateStore)
  }

  def startRest(service: ReadableModelStateStore): Unit = {

    implicit val timeout = Timeout(10 seconds)
    val host = "0.0.0.0"
    val port = MODEL_SERVER_PORT
    val routes: Route = QueriesAkkaHttpResource.storeRoutes(service)

    val _ = Http().bindAndHandle(routes, host, port) map
      { binding => println(s"Starting models observer on port ${binding.localAddress}") } recover {
        case ex =>
          println(s"Models observer could not bind to $host:$port/ ${ex.getMessage}")
      }
    ()
  }
}
