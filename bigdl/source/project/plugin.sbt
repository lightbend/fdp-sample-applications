addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.0.0")

addSbtPlugin("com.github.shmishleniy" %% "sbt-deploy-ssh" % "0.1.3")

dependsOn(RootProject(file("../../build-plugin/").toURI))
