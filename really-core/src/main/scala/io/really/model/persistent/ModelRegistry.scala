/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.{ Terminated, ActorRef, ActorLogging }
import akka.persistence.PersistentView
import io.really.model.{ ReferenceField, Model }
import io.really.{ RoutableByR, R, ReallyGlobals }

class ModelRegistry(globals: ReallyGlobals, persistId: String) extends PersistentView with ActorLogging {
  import ModelRegistry._

  override def persistenceId: String = persistId
  override def viewId: String = "request-router-view"

  private var routingTable: Map[R, Model] = Map.empty
  private var subscriberActors: Map[R, Set[ActorRef]] = Map.empty
  private var reverseModelReferences: Map[R, List[R]] = Map.empty

  def validR(r: R): Boolean =
    routingTable.contains(r.skeleton)

  def receive: Receive = handleEvent orElse handleCollectionRequest orElse handleGeneralOps

  def handleEvent: Receive = {
    case PersistentModelStore.UpdatedModels(updatedModels) if isPersistent =>
      val rs = updatedModels.map(_.r)
      routingTable ++= updatedModels.map(m => (m.r, m)).toMap
      rs.foreach { r =>
        subscriberActors.getOrElse(r, List.empty).foreach { actor =>
          actor ! ModelOperation.ModelUpdated(r, routingTable(r), reverseModelReferences(r))
        }
      }
      constructReverseReferencesMap()
    case PersistentModelStore.DeletedModels(removedModels) if isPersistent =>
      val rs = removedModels.map(m => m.r)
      rs.foreach { r =>
        subscriberActors.getOrElse(r, List.empty).foreach { actor =>
          context.unwatch(actor)
          actor ! ModelOperation.ModelDeleted(r)
        }
      }
      routingTable --= removedModels.map(_.r)
      subscriberActors = subscriberActors.filterNot(c => rs.contains(c._1))
      constructReverseReferencesMap()
    case PersistentModelStore.AddedModels(newModels) if isPersistent =>
      routingTable ++= newModels.map(m => (m.r, m)).toMap
      constructReverseReferencesMap()
  }

  def handleCollectionRequest: Receive = {
    case RequestModel.GetModel(r, collectionActor) if validR(r) =>
      val modelR = r.skeleton
      subscriberActors += (modelR -> (subscriberActors.getOrElse(modelR, Set.empty) + collectionActor))
      context.watch(collectionActor)
      sender ! ModelResult.ModelObject(routingTable(modelR), reverseModelReferences(modelR))
    case RequestModel.GetModel(r, _) =>
      sender ! ModelResult.ModelNotFound
    case msg: RequestModel.GetModels =>
      sender ! ModelResult.Models(routingTable)
  }

  def handleGeneralOps: Receive = {
    case Terminated(collectionActor) =>
      context.unwatch(collectionActor)
      subscriberActors = subscriberActors.collect {
        case (r, actors) if actors.contains(collectionActor) =>
          (r, actors.filterNot(_ != collectionActor))
        case (r, actors) =>
          (r, actors)
      }
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
  sealed trait RequestModel

  object RequestModel {

    case class GetModel(r: R, sender: ActorRef) extends RequestModel with RoutableByR

    case class GetModels(sender: ActorRef) extends RequestModel

  }

  sealed trait ModelResult
  object ModelResult {

    case object ModelNotFound extends ModelResult

    case class ModelObject(model: Model, referencedCollections: List[R]) extends ModelResult

    case class Models(value: Map[R, Model]) extends ModelResult

    case class ModelFetchError(r: R, reason: String) extends ModelResult

  }

  sealed trait ModelOperation extends RoutableByR

  object ModelOperation {
    case class ModelCreated(r: R, model: Model, referencedCollections: List[R]) extends ModelOperation

    case class ModelUpdated(r: R, model: Model, referencedCollections: List[R]) extends ModelOperation

    case class ModelDeleted(r: R) extends ModelOperation

  }
}
