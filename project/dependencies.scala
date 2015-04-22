/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
import sbt._

object Dependencies {
  val scalatest = "org.scalatest" %% "scalatest" % "2.2.1" % "test"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"
  val wsClient = "io.backchat.hookup" %% "hookup" % "0.2.3"
  val snakeyaml = "org.yaml" % "snakeyaml" % "1.14"
  val commonsIO= "commons-io" % "commons-io" % "2.4"
  val parserCombinator = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"
  val reactivemongo = "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23"
  val embedmongo = "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.2" % "test"
  val slick = "com.typesafe.slick" %% "slick" % "2.1.0"
  val h2 = "com.h2database" % "h2" % "1.3.175"
  val playScalaTest = "org.scalatestplus" %% "play" % "1.2.0" % "test"
  val leveldb = "org.iq80.leveldb" % "leveldb" % "0.7"
  val jwtScala = "io.really" %% "jwt-scala" % "1.2.2"
  val akkaPersistenceMongo = "com.github.ironfish" %% "akka-persistence-mongo-casbah"  % "0.7.5" % "compile"

  object Akka {
    private val akkaBase = "com.typesafe.akka"
    private val version = "2.3.9"

    val agent = akkaBase %% "akka-agent" % version
    val testKit = akkaBase %% "akka-testkit" % version % "test"
    val actor = akkaBase %% "akka-actor" % version
    val cluster = akkaBase %% "akka-cluster" % version
    val contrib = akkaBase %% "akka-contrib" % version
    val slf4j = akkaBase %%  "akka-slf4j"	%	version
    val persistance = akkaBase %% "akka-persistence-experimental" % version
    val sharedInMemJournal =  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.0.0"
    val multiNode = akkaBase %% "akka-multi-node-testkit" % version
  }

}
