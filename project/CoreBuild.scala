/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
import sbt._
import Keys._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import play.Play.autoImport._


object CoreBuild {

  import Dependencies._

  val settings = BuildSettings.default ++ SbtMultiJvm.multiJvmSettings ++ Seq(
    libraryDependencies ++= Seq(
      parserCombinator,
      scalatest,
      Akka.actor,
      Akka.testKit,
      Akka.contrib,
      Akka.slf4j,
      Akka.sharedInMemJournal,
      logback,
      snakeyaml,
      json,
      snakeyaml,
      embedmongo,
      reactivemongo,
      slick,
      h2,
      Akka.multiNode
    ),
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults) =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )
}
