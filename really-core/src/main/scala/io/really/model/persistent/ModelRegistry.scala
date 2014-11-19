/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.{ Terminated, ActorRef, ActorLogging }
import akka.persistence.PersistentView
import io.really.model.{ ReferenceField, Model }
import io.really.{ RoutableByR, R, ReallyGlobals }

class ModelRegistry(globals: ReallyGlobals) extends PersistentView with ActorLogging {
  import ModelRegistry._

  override def persistenceId: String = "model-registry-persistent"
  override def viewId: String = "request-router-view"

  private var routingTable: Map[R, Model] = Map.empty
  private var collectionActors: Map[R, Set[ActorRef]] = Map.empty
  private var reverseModelReferences: Map[R, List[R]] = Map.empty

  def validR(r: R): Boolean =
    routingTable.contains(r.skeleton)

  def receive: Receive = handleEvent orElse handleCollectionRequest orElse handleGeneralOps

  def handleEvent: Receive = {
    case PersistentModelStore.UpdatedModels(updatedModels) =>
      val rs = updatedModels.map(_.r)
      routingTable ++= updatedModels.map(m => (m.r, m)).toMap
      rs.foreach { r =>
        collectionActors.getOrElse(r, List.empty).foreach { actor =>
          actor ! ModelOperation.ModelUpdated(r, routingTable(r), reverseModelReferences(r))
        }
      }
      constructReverseReferencesMap()
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
      constructReverseReferencesMap()
    case PersistentModelStore.AddedModels(newModels) =>
      routingTable ++= newModels.map(m => (m.r, m)).toMap
      constructReverseReferencesMap()
  }

  def handleCollectionRequest: Receive = {
    case CollectionActorMessage.GetModel(r, collectionActor) if validR(r) =>
      collectionActors += (r -> (collectionActors.getOrElse(r, Set.empty) + collectionActor))
      context.watch(collectionActor)
      sender ! ModelResult.ModelObject(routingTable(r), reverseModelReferences(r))
    case CollectionActorMessage.GetModel(r, _) =>
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

  def handleGeneralOp: Receive = {
    case Terminated(actorRef) =>
      context.unwatch(actorRef)
    //TODO remove from collectionActors
  }

  def constructReverseReferencesMap(): Unit =
    reverseModelReferences = routingTable.values.map {
      m =>
        val referenceFields: List[ReferenceField[_]] = m.fields.values.collect {
          case f: ReferenceField[_] => f
        }.toList

        (m.r, referenceFields.map(f => f.collectionR))
    }.toMap

}

object ModelRegistry {
  sealed trait CollectionActorMessage
  object CollectionActorMessage {

    case class GetModel(r: R, sender: ActorRef) extends CollectionActorMessage with RoutableByR

  }

  sealed trait ModelResult
  object ModelResult {

    case object ModelNotFound extends ModelResult

    case class ModelObject(model: Model, referencedCollections: List[R]) extends ModelResult

    case class ModelFetchError(r: R, reason: String) extends ModelResult

  }

  sealed trait ModelOperation extends RoutableByR

  object ModelOperation {
    case class ModelCreated(r: R, model: Model, referencedCollections: List[R]) extends ModelOperation

    case class ModelUpdated(r: R, model: Model, referencedCollections: List[R]) extends ModelOperation

    case class ModelDeleted(r: R) extends ModelOperation

  }
}
