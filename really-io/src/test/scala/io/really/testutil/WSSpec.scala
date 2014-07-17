package io.really.testutil

import scala.concurrent.duration._
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import io.backchat.hookup._
import io.really.testutil._

class WSSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  def this(name: String) =
    this(ActorSystem(name))

  val app = new io.really.boot.ReallyApp

  override def afterAll: Unit = {
    app.shutdown()
    app.awaitTermination()
    system.shutdown()
    system.awaitTermination()
  }

  lazy val client = {
    Thread.sleep(2000)
    val ws = WSClient("ws://localhost:8080")(self)
    expectMsg(Connected)
    ws
  }

}
