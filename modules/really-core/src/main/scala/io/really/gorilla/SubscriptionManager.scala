/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import scala.collection.mutable.Map
import akka.actor._
import _root_.io.really.RequestContext
import _root_.io.really.rql.RQL.Query
import _root_.io.really.Result
import _root_.io.really.model.FieldKey
import _root_.io.really.protocol.SubscriptionFailure
import _root_.io.really.{ R, ReallyGlobals }
import _root_.io.really.Request.{ SubscribeOnObjects, UnsubscribeFromObjects }
import io.really.Result.SubscribeResult
import io.really.protocol.SubscriptionOpResult

/**
 * SubscriptionManager is one actor per node and responsible for managing the subscriptions on objects, rooms and
 * queries
 * @param globals
 */
class SubscriptionManager(globals: ReallyGlobals) extends Actor with ActorLogging {

  type SubscriberIdentifier = ActorPath

  import SubscriptionManager._

  private[gorilla] var rSubscriptions: Map[(SubscriberIdentifier, R), InternalRSubscription] = Map.empty
  private[gorilla] var roomSubscriptions: Map[SubscriberIdentifier, InternalRSubscription] = Map.empty

  def failedToRegisterNewSubscription(originalSender: ActorRef, r: R, newSubscriber: ActorRef, reason: String) = {
    newSubscriber ! SubscriptionFailure(r, 500, reason)
    sender() ! SubscriptionFailure(r, 500, reason)
    log.error(reason)
  }

  def receive = commonHandler orElse objectSubscriptionsHandler orElse roomSubscriptionsHandler

  def commonHandler: Receive = {
    case Terminated(actor) =>
      //TODO Handle death of subscribers
      log.info("Actor Terminated" + actor)
  }

  /**
   * Handles the messages of Objects subscriptions
   * case `SubscribeOnObject` is expected to come externally as a request to subscribe on an object
   * case `UnsubscribeFromObject` is expected to come externally as a request to unsubscribe on an object
   * case `SubscribeOnR` is expected to come internally from the Subscribe request aggregator
   * case `UnubscribeFromR` is expected to come internally from the Unubscribe request aggregator
   */
  def objectSubscriptionsHandler: Receive = {
    case request: SubscribeOnObjects =>
      request.body.subscriptions.length match {
        case 1 =>
          val subscriptionOp = request.body.subscriptions.head
          self ! SubscribeOnR(RSubscription(
            request.ctx,
            subscriptionOp.r,
            subscriptionOp.fields,
            subscriptionOp.rev,
            sender(),
            request.pushChannel
          ))
        case len if len > 1 =>
          val delegate = sender()
          context.actorOf(Props(new SubscribeAggregator(request, delegate, self, globals)))
      }

    case UnsubscribeFromObjects(ctx, body, pushChannel) =>
      body.subscriptions.foreach {
        r =>
          ???
      }
      ???
    case SubscribeOnR(subData) =>
      val replyTo = sender()
      rSubscriptions.get((subData.pushChannel.path, subData.r)).map {
        rSub =>
          rSub.objectSubscriber ! UpdateSubscriptionFields(subData.fields)
      }.getOrElse {
        globals.gorillaEventCenter ! NewSubscription(replyTo, subData)
      }
    case ObjectSubscribed(subData, replyTo, objectSubscriber) =>
      rSubscriptions += (subData.pushChannel.path, subData.r) -> InternalRSubscription(objectSubscriber, subData.r)
      context.watch(objectSubscriber) //TODO handle death
      context.watch(subData.pushChannel) //TODO handle death
      if (replyTo == self) {
        subData.requestDelegate ! SubscribeResult(Set(SubscriptionOpResult(subData.r, subData.fields)))
      } else {
        replyTo ! SubscriptionDone(subData.r)
      }
    case UnsubscribeFromR(subData) => //TODO Ack the delegate
      rSubscriptions.get((subData.pushChannel.path, subData.r)).map {
        rSub =>
          rSub.objectSubscriber ! Unsubscribe
          rSubscriptions -= ((subData.pushChannel.path, subData.r))
      }
  }

  def roomSubscriptionsHandler: Receive = {
    case SubscribeOnRoom(subData) => ??? //TODO Handle Room subscriptions

    case UnsubscribeFromRoom(subData) =>
      roomSubscriptions.get(subData.pushChannel.path).map {
        roomSub =>
          roomSub.objectSubscriber ! Unsubscribe
          roomSubscriptions -= subData.pushChannel.path
      }

  }

}

object SubscriptionManager {

  case class InternalRSubscription(objectSubscriber: ActorRef, r: R)

  case class SubscribeOnR(rSubscription: RSubscription)

  case class SubscribeOnQuery(requester: ActorRef, ctx: RequestContext, query: Query, passOnResults: Result.ReadResult)

  case class SubscribeOnRoom(rSubscription: RoomSubscription)

  case class UnsubscribeFromR(roomSubscription: RSubscription)

  case class UnsubscribeFromRoom(roomSubscription: RoomSubscription)

  case class UpdateSubscriptionFields(fields: Set[FieldKey])

  case object Unsubscribe

  case class ObjectSubscribed(subData: RSubscription, replyTo: ActorRef, objectSubscriber: ActorRef)

  case class SubscriptionDone(r: R)

}
