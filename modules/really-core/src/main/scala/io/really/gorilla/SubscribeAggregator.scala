/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.{ ActorLogging, ActorRef, Actor }
import akka.contrib.pattern.Aggregator
import io.really.RequestContext
import io.really.Request.SubscribeOnObjects
import io.really.gorilla.SubscriptionManager.{ SubscriptionDone, SubscribeOnR }
import io.really.protocol.SubscriptionBody
import io.really.ReallyGlobals
import io.really.Result.SubscribeResult
import io.really.protocol.SubscriptionOpResult
import scala.collection.mutable.Set

class SubscribeAggregator(request: SubscribeOnObjects, delegate: ActorRef, subscriptionManager: ActorRef,
    globals: ReallyGlobals) extends Actor with Aggregator with ActorLogging {

  import context._
  import SubscribeAggregator._

  new SubscribeAggregatorImpl(request.ctx, delegate, request.body, request.pushChannel)

  class SubscribeAggregatorImpl(ctx: RequestContext, requestDelegate: ActorRef, body: SubscriptionBody,
      pushChannel: ActorRef) {

    val results = Set.empty[SubscriptionOpResult]
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
          results += SubscriptionOpResult(r, rSub.fields)
          processedCount += 1
          collectSubscriptions()
      }
    }

    def collectSubscriptions(force: Boolean = false) {
      if (processedCount == body.subscriptions.size || force) {
        timer.cancel()
        requestDelegate ! SubscribeResult(results.toSet)
        context.stop(self)
      }
    }
  }

}

object SubscribeAggregator {

  case object TimedOut

  case object UnsupportedResponse

}
