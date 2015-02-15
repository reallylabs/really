/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.{ ActorLogging, Actor }
import io.really.ReallyGlobals
import io.really.gorilla.SubscriptionManager.Unsubscribe

class EventStreamer(subscription: RSubscription, globals: ReallyGlobals) extends Actor with ActorLogging {
  def receive = {
    case Unsubscribe => ???
  }
}
