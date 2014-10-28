/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.cluster.routing.{ClusterRouterPoolSettings, ClusterRouterPool}
import akka.persistence.PersistentView
import akka.routing.RoundRobinPool
import io.really._
import akka.actor.{ActorRef, ActorLogging}
import akka.persistence.PersistentView


class ModelRegistryRouter(globals: ReallyGlobals) extends PersistentView with ActorLogging {
  import ModelRegistryRouter._

  override def persistenceId: String = "model-registry-persistent"
  override def viewId: String = "model-registry-view"

  private var routingTable: Map[R, Model] = Map.empty
  private var collectionActors: Map[R, Set[ActorRef]] = Map.empty

  private def validR(r: R): Boolean =
    routingTable.contains(r.skeleton)

  def receive: Receive = handleEvent orElse handleCollectionRequest orElse handleRequest

  def handleEvent: Receive = {
    case PersistentModelStore.UpdatedModels(updatedModels) =>
      val rs = updatedModels.map(_.r)
      routingTable ++= updatedModels.map(m => (m.r, m)).toMap
      rs.foreach { r =>
        collectionActors.getOrElse(r, List.empty).foreach { actor =>
          actor ! ModelOperation.ModelUpdated(r, routingTable(r))
        }
      }
    case PersistentModelStore.DeletedModels(removedModels) =>
      val rs = removedModels.map(m => m.r)
      rs.foreach { r =>
        collectionActors.getOrElse(r, List.empty).foreach { actor =>
          context.unwatch(actor)
          actor ! ModelOperation.ModelDeleted(r)
        }
      }
      routingTable --= removedModels.map(_.r)
      collectionActors = collectionActors.filterNot(c => rs.contains(c._1))
    case PersistentModelStore.AddedModels(newModels) =>
      routingTable ++= newModels.map(m => (m.r, m)).toMap
  }

  def handleCollectionRequest: Receive = {
    case CollectionActorMessage.GetModel(r) if validR(r) =>
      val collectionActor = sender
      collectionActors += (r -> (collectionActors.getOrElse(r, Set.empty) + collectionActor))
      context.watch(collectionActor)
      collectionActor ! ModelResult.ModelObject(routingTable(r))
    case CollectionActorMessage.GetModel(r) =>
      sender ! ModelResult.ModelNotFound
  }

  def handleRequest: Receive = {
    case req: Request with RoutableToCollectionActor if validR(req.r) =>
      globals.collectionActor forward req
    case req: Request with RoutableToCollectionActor =>
      sender ! ModelRouterResponse.RNotFound(req.r)
    case req: Request =>
      sender ! ModelRouterResponse.UnsupportedCmd(req.getClass.getName)
  }

}

object ModelRegistryRouter {
  trait ModelRouterResponse

  object ModelRouterResponse {

    case class RNotFound(r: R) extends ModelRouterResponse

    case class UnsupportedCmd(cmd: String) extends ModelRouterResponse

  }

  sealed trait CollectionActorMessage
  object CollectionActorMessage {

    case class GetModel(r: R) extends CollectionActorMessage with RoutableByR

  }

  sealed trait ModelResult
  object ModelResult {

    case object ModelNotFound extends ModelResult

    case class ModelObject(model: Model) extends ModelResult

    case class ModelFetchError(r: R, reason: String) extends ModelResult

  }

  sealed trait ModelOperation extends RoutableByR

  object ModelOperation {

    case class ModelUpdated(r: R, model: Model) extends ModelOperation

    case class ModelDeleted(r: R) extends ModelOperation

  }
}
