/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import _root_.io.really._
import akka.actor.ActorLogging
import akka.persistence.PersistentView

class RequestRouter(globals: ReallyGlobals, persistId: String) extends PersistentView with ActorLogging {
  import RequestRouter._

  override def persistenceId: String = persistId
  override def viewId: String = "request-router-view"

  private var models: List[R] = List.empty

  def validR(r: R): Boolean =
    models.contains(r.skeleton)

  def receive: Receive = handleEvent orElse handleRequest

  def handleEvent: Receive = {
    case PersistentModelStore.DeletedModels(removedModels) =>
      models = models diff removedModels.map(_.r)

    case PersistentModelStore.AddedModels(newModels) =>
      models ++= newModels.map(_.r)
  }

  def handleRequest: Receive = {
    case req: Request with RoutableToCollectionActor if validR(req.r) =>
      globals.collectionActor forward req
    case req: Request with RoutableToCollectionActor =>
      sender ! RequestRouterResponse.RNotFound(req.r)
    case req: Request with RoutableToReadHandler if validR(req.r) =>
      globals.readHandler forward req
    case req: Request with RoutableToReadHandler =>
      sender ! RequestRouterResponse.RNotFound(req.r)
    case req: Request with RoutableToSubscriptionManager with RoutableByR if validR(req.r) =>
      globals.subscriptionManager forward req
    case req: Request with RoutableToSubscriptionManager with RoutableByR =>
      sender ! RequestRouterResponse.RNotFound(req.r)
    case req: Request with RoutableToSubscriptionManager =>
      globals.subscriptionManager forward req
    case req: Request =>
      sender ! RequestRouterResponse.UnsupportedCmd(req.getClass.getName)
  }
}

object RequestRouter {

  trait RequestRouterResponse

  object RequestRouterResponse {

    case class RNotFound(r: R) extends RequestRouterResponse

    case class UnsupportedCmd(cmd: String) extends RequestRouterResponse

  }
}
