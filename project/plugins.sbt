logLevel := Level.Warn

resolvers += Resolver.sonatypeRepo("releases")

resolvers += "Bintray Repository" at "https://dl.bintray.com/shmishleniy/"

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
