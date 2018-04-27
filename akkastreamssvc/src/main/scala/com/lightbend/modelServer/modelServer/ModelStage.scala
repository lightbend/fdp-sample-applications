package com.lightbend.modelServer.modelServer

import akka.stream.{FanInShape2, _}
import akka.stream.stage.{GraphStageLogicWithLogging, _}
import com.lightbend.configuration.{GrafanaClient, GrafanaConfig, InfluxDBClient, InfluxDBConfig}
import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelServer.model.Model
import com.lightbend.modelServer.model.PMML.PMMLModel
import com.lightbend.modelServer.model.tensorflow.TensorFlowModel
import com.lightbend.modelServer.{ModelToServe, ModelToServeStats}

class ModelStage(influxDBConfig: InfluxDBConfig, grafanaConfig: GrafanaConfig)
      extends GraphStageWithMaterializedValue[FanInShape2[WineRecord, ModelToServe, Option[Double]], ReadableModelStateStore] {

  private val factories = Map(
    ModelDescriptor.ModelType.PMML -> PMMLModel,
    ModelDescriptor.ModelType.TENSORFLOW -> TensorFlowModel
  )

  private val influx = new InfluxDBClient(influxDBConfig)
  private val grafana = new GrafanaClient(grafanaConfig, influxDBConfig)

  val dataRecordIn = Inlet[WineRecord]("dataRecordIn")
  val modelRecordIn = Inlet[ModelToServe]("modelRecordIn")
  val scoringResultOut = Outlet[Option[Double]]("scoringOut")

  override val shape: FanInShape2[WineRecord, ModelToServe, Option[Double]] = new FanInShape2(dataRecordIn, modelRecordIn, scoringResultOut)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, ReadableModelStateStore) = {

    val logic = new GraphStageLogicWithLogging(shape) {
      // state must be kept in the Logic instance, since it is created per stream materialization
      private var currentModel: Option[Model] = None
      private var newModel: Option[Model] = None
      var currentState: Option[ModelToServeStats] = None // exposed in materialized value
      private var newState: Option[ModelToServeStats] = None

      // TODO the pulls needed to get the stage actually pulling from the input streams
      override def preStart(): Unit = {
        tryPull(modelRecordIn)
        tryPull(dataRecordIn)
      }

      setHandler(modelRecordIn, new InHandler {
        override def onPush(): Unit = {
          val model = grab(modelRecordIn)
          println(s"New model - $model")
          newState = Some(new ModelToServeStats(model))
          newModel = for {
            factory <- factories.get(model.modelType)
            _model <- factory.create(model)
          } yield _model

          pull(modelRecordIn)
        }
      })

      setHandler(dataRecordIn, new InHandler {
        override def onPush(): Unit = {
          val record = grab(dataRecordIn)
          newModel match {
            case Some(model) => {
              // close current model first
              currentModel match {
                case Some(m) => m.cleanup()
                case _ =>
              }
              // Update model
              currentModel = Some(model)
              currentState = newState
              newModel = None
            }
            case _ =>
          }
          currentModel match {
            case Some(model) => {
              val start = System.currentTimeMillis()
              val quality = model.score(record.asInstanceOf[AnyVal]).asInstanceOf[Double]
              val duration = System.currentTimeMillis() - start
              println(s"Calculated quality - $quality calculated in $duration ms")
              influx.writePoint("Akka", currentState.get.name, quality, duration)
              currentState.get.incrementUsage(duration)
              push(scoringResultOut, Some(quality))
            }
            case _ => {
              println("No model available - skipping")
              push(scoringResultOut, None)
            }
          }
          pull(dataRecordIn)
        }
      })

      setHandler(scoringResultOut, new OutHandler {
        override def onPull(): Unit = {
        }
      })
    }
    // we materialize this value so whoever runs the stream can get the current serving info
    val readableModelStateStore = new ReadableModelStateStore() {
      override def getCurrentServingInfo: ModelToServeStats = logic.currentState.getOrElse(ModelToServeStats.empty)
    }
    new Tuple2[GraphStageLogic, ReadableModelStateStore](logic, readableModelStateStore)
  }
}