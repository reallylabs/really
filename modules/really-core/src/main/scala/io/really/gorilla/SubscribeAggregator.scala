/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.{ ActorLogging, ActorRef, Actor }
import akka.contrib.pattern.Aggregator
import io.really.R
import io.really.RequestContext
import io.really.Request.SubscribeOnObject
import io.really.gorilla.SubscriptionManager.{ SubscriptionDone, SubscribeOnR }
import io.really.protocol.SubscriptionBody
import scala.collection.mutable.ArrayBuffer
import io.really.ReallyGlobals

class SubscribeAggregator(request: SubscribeOnObject, delegate: ActorRef, subscriptionManager: ActorRef,
    globals: ReallyGlobals) extends Actor with Aggregator with ActorLogging {

  import context._
  import SubscribeAggregator._

  new SubscribeAggregatorImpl(request.ctx, delegate, request.body, request.pushChannel)

  class SubscribeAggregatorImpl(ctx: RequestContext, requestDelegate: ActorRef, body: SubscriptionBody,
      pushChannel: ActorRef) {

    val results = ArrayBuffer.empty[R]
    private[this] var processedCount = 0

    if (body.subscriptions.size > 0) {
      body.subscriptions.foreach {
        op =>
          subscribeOnR(RSubscription(ctx, op.r, op.fields, op.rev, requestDelegate, pushChannel))
      }
    } else {
      collectSubscriptions()
    }

    val timer = context.system.scheduler.scheduleOnce(
      globals.config.GorillaConfig.waitForSubscriptionsAggregation,
      self, TimedOut
    )
    expect {
      case TimedOut =>
        log.warning(s"Subscribe Aggregator timed out while waiting the subscriptions to be fulfilled for requester:" +
          s" $requestDelegate")
        collectSubscriptions(force = true)
    }

    def subscribeOnR(rSub: RSubscription) = {
      subscriptionManager ! SubscribeOnR(rSub)
      expectOnce {
        case SubscriptionDone(r) =>
          results += r
          processedCount += 1
          collectSubscriptions()
      }
    }

    def collectSubscriptions(force: Boolean = false) {
      if (processedCount == body.subscriptions.size || force) {
        timer.cancel()
        requestDelegate ! SubscribeAggregator.Subscribed(results.toSet)
        context.stop(self)
      }
    }
  }

}

object SubscribeAggregator {

  case class Subscribed(rs: Set[R])

  case object TimedOut

  case object UnsupportedResponse

}
