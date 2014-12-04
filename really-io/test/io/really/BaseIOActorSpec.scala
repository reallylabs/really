/*
* Copyright (C) 2014-2015 Really Inc. <http://really.io>
*/
package io.really

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.testkit.{ TestKit, ImplicitSender }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import _root_.io.really.io.boot._
import _root_.io.really.io.IOConfig
import org.joda.time.DateTime
import org.scalatest._

abstract class BaseIOActorSpec(conf: IOConfig = IOTestConf.getConfig()) extends TestKit(TestActorSystem(
  "TestActorSystem",
  conf
)) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val executionContext = system.dispatcher
  implicit val config: IOConfig = conf
  val ioGlobals = new TestIOGlobals(config)

  override def beforeAll() = {
    ioGlobals.boot()
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }
}

object TestActorSystem {
  def apply(name: String, conf: IOConfig): ActorSystem = {
    ActorSystem(name, conf.ioConfig)
  }
}

object IOTestConf {
  def getConfig(): IOConfig = {
    val config = ConfigFactory.load("really-io-test")
    new DefaultIOConfig(config)
  }
}
