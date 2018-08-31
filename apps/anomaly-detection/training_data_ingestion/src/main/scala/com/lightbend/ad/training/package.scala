package com.lightbend.ad.training

import java.nio.charset.Charset

package object ingestion {
  final val CHARSET = Charset.forName("UTF-8")

  implicit def asFiniteDuration(d: java.time.Duration) =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)
}
