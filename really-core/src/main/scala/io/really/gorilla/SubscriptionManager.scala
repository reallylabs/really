/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.util.Timeout
import io.really.protocol.SubscriptionFailure
import scala.collection.mutable.Map
import _root_.io.really.model.FieldKey
import akka.actor._
import _root_.io.really.{ R, ReallyGlobals, RequestContext }
import _root_.io.really.rql.RQL.Query
import _root_.io.really.Result
import _root_.io.really.WrappedSubscriptionRequest.{ WrappedSubscribe, WrappedUnsubscribe }
import akka.pattern.{ AskTimeoutException, ask }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/**
 * SubscriptionManager is sharded actor and responsible for managing the subscriptions on objects, rooms and queries
 * @param globals
 */
class SubscriptionManager(globals: ReallyGlobals) extends Actor with ActorLogging {

  type SubscriberIdentifier = ActorPath

  import SubscriptionManager._

  private[gorilla] var rSubscriptions: Map[SubscriberIdentifier, InternalRSubscription] = Map.empty
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
   * case `WrappedSubscribe` is expected to come externally as a request to subscribe on an object
   * case `WrappedUnubscribe` is expected to come externally as a request to unsubscribe on an object
   * case `SubscribeOnR` is expected to come internally from the Subscribe request aggregator
   * case `UnubscribeFromR` is expected to come internally from the Unubscribe request aggregator
   */
  def objectSubscriptionsHandler: Receive = {
    case request: WrappedSubscribe =>
      context.actorOf(Props(new SubscribeAggregator(self, globals))) forward request
    case request: WrappedUnsubscribe =>
      ???
    case SubscribeOnR(subData) =>
      rSubscriptions.get(subData.pushChannel.path).map {
        rSub =>
          rSub.objectSubscriber ! UpdateSubscriptionFields(subData.fields.getOrElse(Set.empty))
      }.getOrElse {
        implicit val timeout = Timeout(globals.config.GorillaConfig.waitForGorillaCenter)
        val originalSender = sender()
        val result = globals.gorillaEventCenter ? NewSubscription(subData)
        result.onSuccess {
          case ObjectSubscribed(objectSubscriber) =>
            rSubscriptions += subData.pushChannel.path -> InternalRSubscription(objectSubscriber, subData.r)
            context.watch(objectSubscriber) //TODO handle death
            context.watch(subData.pushChannel) //TODO handle death
            originalSender ! SubscriptionDone
          case _ =>
            val reason = s"Gorilla Center replied with unexpected response to new subscription request: $subData"
            failedToRegisterNewSubscription(originalSender, subData.r, subData.pushChannel, reason)
        }
        result.onFailure {
          case e: AskTimeoutException =>
            val reason = s"SubscriptionManager timed out waiting for the Gorilla center response for" +
              s" subscription $subData"
            failedToRegisterNewSubscription(originalSender, subData.r, subData.pushChannel, reason)
          case NonFatal(e) =>
            val reason = s"Unexpected error while asking the Gorilla Center to establish a new subscription: $subData"
            failedToRegisterNewSubscription(originalSender, subData.r, subData.pushChannel, reason)
        }
      }
    case UnsubscribeFromR(subData) => //TODO Ack the delegate
      rSubscriptions.get(subData.pushChannel.path).map {
        rSub =>
          rSub.objectSubscriber ! Unsubscribe
          rSubscriptions -= subData.pushChannel.path
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

  case class ObjectSubscribed(objectSubscriber: ActorRef)

  case object SubscriptionDone

}
