import sbt._
import Keys._

object IOBuild {

  import Dependencies._

  val settings = BuildSettings.default ++ Seq(
    libraryDependencies ++= Seq(
      scalatest,
      Akka.actor,
      Akka.testKit,
      Akka.contrib,
      Playframework.functional,
      Playframework.json,
      Spray.can,
      Spray.routing,
      Spray.websocket,
      Spray.testkit % "test",
      wsClient % "test"
    )
  )
}
