package com.lightbend.fdp.sample

import java.nio.charset.Charset
import cats.syntax.either._
import java.time.OffsetDateTime
import io.circe._, io.circe.generic.semiauto._
import kstream.models.LogRecord

package object kstream {
  final val CHARSET = Charset.forName("UTF-8")

  implicit val encodeOffsetDateTime: Encoder[OffsetDateTime] = Encoder.encodeString.contramap[OffsetDateTime](_.toString)

  implicit val decodeInstant: Decoder[OffsetDateTime] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(OffsetDateTime.parse(str)).leftMap(t => "OffsetDateTime")
  }
  
  implicit val logRecordDecoder: Decoder[LogRecord] = deriveDecoder[LogRecord]
  implicit val logRecordEncoder: Encoder[LogRecord] = deriveEncoder[LogRecord]

  implicit def asFiniteDuration(d: java.time.Duration) =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)
}
