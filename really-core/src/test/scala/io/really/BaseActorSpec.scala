package io.really

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._

abstract class BaseActorSpec(testActor: String, conf: Config = TestConf()) extends TestKit(TestActorSystem(testActor,
  conf)) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val executionContext = system.dispatcher
  val config: ReallyConfig = new ReallyConfig(conf)
  val globals = new TestReallyGlobals(config)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }
}

object TestActorConf {
  def apply(): Config = {
    ConfigFactory.parseString( s"""
      akka.loggers = ["akka.testkit.TestEventListener"]
	    akka.loglevel = DEBUG
      akka.actor.debug.receive = on
      akka.actor.debug.autoreceive = on
      akka.actor.debug.lifecycle = on
      akka.log-dead-letters = off
	    akka.remote.log-remote-lifecycle-events = off
		  akka.remote.netty.tcp.port = 0
	    akka.cluster.auto-down = on
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
	    """)
  }
}

object TestActorSystem {
  def apply(name: String, conf: Config): ActorSystem = {
    ActorSystem(name, conf)
  }
}

object TestConf {
  def apply(): Config = {
    TestActorConf().withFallback(ConfigFactory.load())
  }
}