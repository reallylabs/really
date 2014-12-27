/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
import sbt._
import Keys._
import play.Play.autoImport._
import PlayKeys._

object AuthBuild {

  import Dependencies._

  val settings = BuildSettings.default ++ Seq(
    libraryDependencies ++= Seq(
      playScalaTest,
      jwtScala,
      cache
    ),
    parallelExecution in Test := false,
    javaOptions in Test ++= Seq("-Dconfig.resource=really-test.conf", "-Dlogger.resource=test-logger.xml", "-Dlogback.configurationFile=test-logger.xml")
  )
}