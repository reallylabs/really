/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.{ Stash, ActorLogging, Actor }
import akka.contrib.pattern.DistributedPubSubMediator.{ SubscribeAck, Subscribe }
import io.really.gorilla.SubscriptionManager.{ UpdateSubscriptionFields, Unsubscribe }
import io.really.{ R, ReallyGlobals }
import play.api.libs.json.JsObject

class SubscriptionActor(subscription: RSubscription, globals: ReallyGlobals) extends Actor with ActorLogging with Stash {

  globals.mediator ! Subscribe(subscription.r.toString, self)

  def receive = Actor.emptyBehavior

  def shutdown() = ???

  def ready: Receive = {
    case msg: PersistentCreatedEvent => ???
    case msg: PersistentUpdatedEvent => ???
    case msg: PersistentDeletedEvent => ???
    case UpdateSubscriptionFields => ???
    case msg: StreamingEvent => ???
    case Unsubscribe => ???
  }
}

//object SubscriptionActor {
//
//  trait PushMessageMeta
//
//  object PushMessageMeta {
//
//    case class CreateMeta(subscriptionID: SubscriptionID)
//
//    case class DeleteMeta(deletedBy: R)
//
//  }
//
//  trait PushMessage {
//    val r: R
//    val evt: PushEventType
//  }
//
//  object PushMessage {
//
//    case class PushCreateMessage(r: R, meta: PushMessageMeta.CreateMeta,
//                                 body: JsObject) extends PushMessage {
//      val evt: PushEventType = "created"
//    }
//
//    case class PushUpdatedMessage(r: R, body: JsObject) extends PushMessage {
//      val evt: PushEventType = "updated"
//    }
//
//    case class PushDeletedMessage(r: R, meta: PushMessageMeta.DeleteMeta,
//                                  body: JsObject) extends PushMessage {
//      val evt: PushEventType = "deleted"
//    }
//
//  }
//
//}