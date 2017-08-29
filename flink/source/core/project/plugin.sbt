addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.5")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.0.0")

dependsOn(RootProject(file("../../../build-plugin/").toURI))
