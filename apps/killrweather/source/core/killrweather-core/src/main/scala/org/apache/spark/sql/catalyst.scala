package org.apache.spark.sql

/**
  * Catalyst is a library for manipulating relational query plans.  All classes in catalyst are
  * considered an internal API to Spark SQL and are subject to change between minor releases.
  * This is based on https://datastax-oss.atlassian.net/browse/SPARKC-530
  *     The Cassandra Spark Connector does not work correctly under Spark 2.3, potentially due to a change
  *     in the reflection lock used by Spark according to richard@datastax.com. Same code does work under
  *     Spark 2.2. As a temporary fix in my project, I added back the package object that was removed in Spark 2.3:
  *     https://github.com/apache/spark/blob/v2.2.1/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/package.scala

Assignee:	 Unassigned
Reporter:	 timfpark Tim Park
Votes:	1 Vote for this issue
Watchers:	4 Start watching this issue
Created:	07/Mar/18 6:56 AM

  */
package object catalyst {
  /**
    * A JVM-global lock that should be used to prevent thread safety issues when using things in
    * scala.reflect.*.  Note that Scala Reflection API is made thread-safe in 2.11, but not yet for
    * 2.10.* builds.  See SI-6240 for more details.
    */
  protected[sql] object ScalaReflectionLock
}