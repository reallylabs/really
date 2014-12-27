/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
import play.Play.autoImport._
import sbt.Keys._
import sbt._


object UtilsBuild {

  import Dependencies._

  val settings = BuildSettings.default ++ Seq(
    libraryDependencies ++= Seq(
      parserCombinator,
      scalatest,
      json,
      logback
    ),
    // disable parallel tests
    parallelExecution in Test := false
  )
}
