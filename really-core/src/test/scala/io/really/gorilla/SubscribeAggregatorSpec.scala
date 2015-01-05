/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.Props
import akka.testkit.{ EventFilter, TestProbe, TestActorRef }
import com.typesafe.config.ConfigFactory
import io.really._
import _root_.io.really.WrappedSubscriptionRequest.WrappedSubscribe
import _root_.io.really.Request.Subscribe
import _root_.io.really.gorilla.SubscribeAggregator.{ Subscribed, UnsupportedResponse }
import _root_.io.really.gorilla.SubscriptionManager.{ SubscriptionDone, SubscribeOnR }
import _root_.io.really.protocol.{ SubscriptionFailure, SubscriptionBody, SubscriptionOp }
import scala.concurrent.duration._

class SubscribeAggregatorSpec(config: ReallyConfig) extends BaseActorSpec(config) {

  def this() = this(new ReallyConfig(ConfigFactory.parseString("""
    really.core.gorilla.wait-for-subscriptions-aggregation = 1s
    really.core.akka.loggers = ["akka.testkit.TestEventListener"],
    really.core.akka.loglevel = WARNING
                                                                """).withFallback(TestConf.getConfig().getRawConfig)))

  val r1 = R / 'users / 201
  val r2 = R / 'users / 202
  val r3 = R / 'users / 203
  val r4 = R / 'users / 204
  val r5 = R / 'users / 205

  "Subscribe Aggregator" should "respond to the WrappedSubscribe correctly only once request then dies" in {
    val probe = TestProbe()
    val manager = TestActorRef[WorkingSubscriptionManagerMock](Props(new WorkingSubscriptionManagerMock(globals)))
    val pushChannel = TestProbe()
    val delegate = TestProbe()
    val aggregator = TestActorRef[SubscribeAggregator](Props(new SubscribeAggregator(manager, globals)))
    probe.watch(aggregator)
    val body = SubscriptionBody(List(SubscriptionOp(r1, 1)))
    aggregator.tell(WrappedSubscribe(Subscribe(ctx, body), pushChannel.ref), delegate.ref)
    delegate.expectMsg(Subscribed(Set(r1)))
    aggregator.tell(WrappedSubscribe(Subscribe(ctx, body), pushChannel.ref), delegate.ref)
    delegate.expectNoMsg()
    probe.expectTerminated(aggregator)
  }

  it should "respond with UnsupportedResponse for unknown messages only once, log the error then dies" in {
    val probe = TestProbe()
    val manager = TestActorRef[WorkingSubscriptionManagerMock](Props(new WorkingSubscriptionManagerMock(globals)))
    val delegate = TestProbe()
    val aggregator = TestActorRef[SubscribeAggregator](Props(new SubscribeAggregator(manager, globals)))
    probe.watch(aggregator)
    val msg = "Something Unsupported"
    EventFilter.error(
      message = s"Subscribe Aggregator got an unexpected response: $msg and going to die",
      occurrences = 1
    ) intercept {
      aggregator.tell(msg, delegate.ref)
    }
    aggregator.tell(msg, delegate.ref)
    delegate.expectMsg(UnsupportedResponse)
    delegate.expectNoMsg()
    probe.expectTerminated(aggregator)
  }

  it should "respond to client with only succeeded subscriptions" in {
    val manager = TestActorRef[WorkingSubscriptionManagerMock](Props(new HalfWorkingSubscriptionManagerMock(globals)))
    val pushChannel = TestProbe()
    val delegate = TestProbe()
    val aggregator = TestActorRef[SubscribeAggregator](Props(new SubscribeAggregator(manager, globals)))
    val body = SubscriptionBody(List(SubscriptionOp(r1, 1), SubscriptionOp(r2, 1), SubscriptionOp(r3, 1),
      SubscriptionOp(r4, 1), SubscriptionOp(r5, 1)))
    aggregator.tell(WrappedSubscribe(Subscribe(ctx, body), pushChannel.ref), delegate.ref)
    delegate.expectMsg(Subscribed(Set(r2, r4)))
  }

  it should "return empty response if the Subscription back-end is failing the requests" in {
    val manager = TestActorRef[WorkingSubscriptionManagerMock](Props(new DisabledSubscriptionManagerMock(globals)))
    val pushChannel = TestProbe()
    val delegate = TestProbe()
    val aggregator = TestActorRef[SubscribeAggregator](Props(new SubscribeAggregator(manager, globals)))
    val body = SubscriptionBody(List(SubscriptionOp(r1, 1), SubscriptionOp(r2, 1), SubscriptionOp(r3, 1),
      SubscriptionOp(r4, 1), SubscriptionOp(r5, 1)))
    aggregator.tell(WrappedSubscribe(Subscribe(ctx, body), pushChannel.ref), delegate.ref)
    delegate.expectMsg(Subscribed(Set.empty))
  }

  it should "return empty response if the Subscription back-end is unresponsive after Timeout" in {
    val manager = TestActorRef[WorkingSubscriptionManagerMock](Props(new UnresponsiveSubscriptionManagerMock(globals)))
    val pushChannel = TestProbe()
    val delegate = TestProbe()
    val aggregator = TestActorRef[SubscribeAggregator](Props(new SubscribeAggregator(manager, globals)))
    val body = SubscriptionBody(List(SubscriptionOp(r1, 1), SubscriptionOp(r2, 1), SubscriptionOp(r3, 1),
      SubscriptionOp(r4, 1), SubscriptionOp(r5, 1)))
    aggregator.tell(WrappedSubscribe(Subscribe(ctx, body), pushChannel.ref), delegate.ref)
    delegate.expectMsg(Subscribed(Set.empty))

    //TODO
    //    val warningFilter: EventFilter = EventFilter.warning(
    //      message = s"Subscribe Aggregator timed out while waiting the subscriptions to be fulfilled" +
    //      s" for requester: $delegate",
    //      occurrences = 1
    //    )
    //    warningFilter.awaitDone(1.minute)
    //    warningFilter.intercept {
    //      aggregator.tell(WrappedSubscribe(Subscribe(ctx, body), pushChannel.ref), delegate.ref)
    //    }

  }

}

class WorkingSubscriptionManagerMock(globals: ReallyGlobals) extends SubscriptionManager(globals) {

  override def receive = {
    case SubscribeOnR(subData) =>
      sender() ! SubscriptionDone
  }

}

class HalfWorkingSubscriptionManagerMock(globals: ReallyGlobals) extends SubscriptionManager(globals) {

  var counter = 0

  override def receive = {
    case SubscribeOnR(subData) =>
      counter += 1
      if (counter % 2 == 0)
        sender() ! SubscriptionDone
      else
        sender() ! SubscriptionFailure(subData.r, 500, "Mocked Failure")
  }

}

class DisabledSubscriptionManagerMock(globals: ReallyGlobals) extends SubscriptionManager(globals) {

  override def receive = {
    case SubscribeOnR(subData) =>
      sender() ! SubscriptionFailure(subData.r, 500, "Mocked Failure")
  }

}

class UnresponsiveSubscriptionManagerMock(globals: ReallyGlobals) extends SubscriptionManager(globals) {

  override def receive = {
    case SubscribeOnR(subData) =>
  }

}
