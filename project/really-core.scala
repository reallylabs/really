import sbt._
import Keys._

object CoreBuild {

  import Dependencies._

  val settings = BuildSettings.default ++ Seq(
    libraryDependencies ++= Seq(
      scalatest,
      Akka.actor,
      Akka.testKit,
      Akka.contrib,
      Akka.slf4j,
      "org.iq80.leveldb" %  "leveldb" %  "0.7",
      Akka.cassandraPersistence,
      logback,
      snakeyaml,
      Playframework.functional,
      Playframework.json
    )
  )
}
