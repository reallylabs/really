/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.{ ActorRef, Terminated, ActorLogging }
import akka.persistence.PersistentView
import io.really.model.Model
import io.really.{ RoutableByR, R, ReallyGlobals }

class ModelRegistry(globals: ReallyGlobals) extends PersistentView with ActorLogging {
  import ModelRegistry._

  override def persistenceId: String = "model-registry-persistent"
  override def viewId: String = "request-router-view"

  private var routingTable: Map[R, Model] = Map.empty
  private var collectionActors: Map[R, Set[ActorRef]] = Map.empty

  def validR(r: R): Boolean =
    routingTable.contains(r.skeleton)

  def receive: Receive = handleEvent orElse handleCollectionRequest orElse handleGeneralOps

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

  def handleGeneralOps: Receive = {
    case Terminated(collectionActor) =>
      context.unwatch(collectionActor)
      collectionActors = collectionActors.collect {
        case (r, actors) if actors.contains(collectionActor) =>
          (r, actors.filterNot(_ != collectionActor))
        case (r, actors) =>
          (r, actors)
      }
  }

}

object ModelRegistry {
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
