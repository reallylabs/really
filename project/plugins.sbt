//**
//* Copyright (C) 2014-2015 Really Inc. <http://really.io>
//**

resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.8")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.0-M2")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.6")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

javaOptions in Global += "-Dfile.encoding=UTF-8"

scalacOptions in Global ++= Seq("-feature", "-deprecation")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")
