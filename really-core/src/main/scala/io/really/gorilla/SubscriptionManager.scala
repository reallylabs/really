/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.{ Props, ActorRef, ActorLogging, Actor }
import akka.contrib.pattern.DistributedPubSubMediator.{ SubscribeAck, Subscribe }
import io.really.{ R, CID, ReallyGlobals }
import io.really.model.FieldKey

class SubscriptionManager(globals: ReallyGlobals) extends Actor with ActorLogging {

  import SubscriptionManager._

  var rSubscriptions: Map[CID, InternalSubscription] = Map.empty
  var roomSubscriptions: Map[CID, InternalSubscription] = Map.empty

  def receive = {
    case SubscribeOnR(subData) =>
      rSubscriptions.get(subData.cid).map {
        rSub =>
          rSub.subscriptionActor ! UpdateSubscriptionFields(subData.fields)
      }.getOrElse {
        val newSubscriber = context.actorOf(Props(classOf[SubscriptionActor], subData, globals), subData.r + "$" + subData.cid)
        rSubscriptions += subData.cid -> InternalSubscription(newSubscriber, subData.cid, subData.r)
        //TODO Send Gorilla the new Subscription request
      }
    case SubscribeOnRoom(subData) => ???
    case UnsubscribeFromR(subData) =>
      rSubscriptions.get(subData.cid).map {
        rSub =>
          rSub.subscriptionActor ! Unsubscribe
      }
    case UnsubscribeFromRoom(subData) =>
      roomSubscriptions.get(subData.cid).map {
        roomSub =>
        //TODO Send Gorilla the new Subscription request
      }
  }
}

object SubscriptionManager {

  case class InternalSubscription(subscriptionActor: ActorRef, cid: CID, r: R)

  case class SubscribeOnR(rSubscription: RSubscription)

  case class SubscribeOnRoom(rSubscription: RoomSubscription)

  case class UnsubscribeFromR(roomSubscription: RSubscription)

  case class UnsubscribeFromRoom(roomSubscription: RoomSubscription)

  case class UpdateSubscriptionFields(fields: Set[FieldKey])

  case object Unsubscribe

}
