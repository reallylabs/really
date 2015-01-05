/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ PoisonPill, Props, ActorRef }
import akka.testkit.{ TestActorRef, TestProbe }
import akka.pattern.ask
import play.api.libs.json.{ Json, JsObject }

import _root_.io.really.protocol.{ GetOpts }

class ReceptionistSpec extends BaseActorSpec {
  import Receptionist._

  val request = Json.obj()

  val cmd = ""

  override lazy val globals = new TestReallyGlobals(config, system) {
    override def requestProps(ctx: RequestContext, replyTo: ActorRef, cmd: String, body: JsObject): Props =
      TestActors.reportingActorProps(self)
  }

  def beAlmost(value: BigDecimal, tolerence: BigDecimal = BigDecimal(0.001)) =
    be >= (value - tolerence) and be <= (value + tolerence)

  "Receptionist" should "create different delegate for each request" in {
    val receptionist = system.actorOf(Props(new Receptionist(globals)))
    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    expectMsgType[ReportingActor.Created]

    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    expectMsgType[ReportingActor.Created]

    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    expectMsgType[ReportingActor.Created]
  }

  "Receptionist" should "count requests" in {
    val receptionist = system.actorOf(Props(new Receptionist(globals)))
    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    receptionist ! DispatchDelegateFor(ctx, cmd, request)

    val f = (receptionist ? GetMetrics).mapTo[Metrics]
    val metrics = Await.result(f, 1.second)
    metrics.messagesCount shouldBe 3
  }

  "Receptionist" should "calculate frequency of requests" in {
    val receptionist = system.actorOf(Props(new Receptionist(globals)))
    val Delay = 100
    Thread.sleep(Delay)
    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    Thread.sleep(Delay)
    receptionist ! DispatchDelegateFor(ctx, cmd, request)
    Thread.sleep(Delay)
    receptionist ! DispatchDelegateFor(ctx, cmd, request)

    val f = (receptionist ? GetMetrics).mapTo[Metrics]
    val metrics = Await.result(f, 1.second)
    metrics.frequency should beAlmost(BigDecimal(3.0 / (3 * Delay)))
  }

}
