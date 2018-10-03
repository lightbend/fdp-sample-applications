package com.lightbend.ad

import java.nio.charset.Charset

package object configuration {
  final val CHARSET = Charset.forName("UTF-8")

  implicit def asFiniteDuration(d: java.time.Duration) =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)
}
