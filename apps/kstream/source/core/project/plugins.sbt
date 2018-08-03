resolvers += "Bintray Repository" at "https://dl.bintray.com/shmishleniy/"

resolvers += "JAnalyse Repository" at "http://www.janalyse.fr/repository/"

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")

addSbtPlugin("com.cavorite" % "sbt-avro-1-8" % "1.1.3")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
lazy val root = project.in( file(".") ).dependsOn(RootProject(file("../../../sbt-common-settings").toURI))

