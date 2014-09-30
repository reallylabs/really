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

  override def persistenceId: String = "persistent-model"
  override def viewId: String = "model-registry-router"

  private var models: List[Model] = List.empty
  private var routingTable: Map[R, Model] = Map.empty
  private var collectionActors: Map[R, Set[ActorRef]] = Map.empty

  def constructRoutingTable(models: List[Model]): Map[R, Model] = {
    models.map(m => (m.r, m)).toMap
  }

  private def validR(r: R): Boolean = {
    routingTable.keySet.exists(k => k == r.skeleton)
  }

  def updateModelsState(updatedModels: List[Model]): Unit = {
    routingTable = constructRoutingTable(models)
  }

  def receive: Receive = handleEvent orElse handleCollectionRequest orElse handleRequest

  def handleEvent: Receive = {
    case PersistentModelStore.UpdatedModels(updatedModels) =>
      val rs = updatedModels.map(m => m.r)
      models = models.filter(m => !rs.contains(m.r)) ++ updatedModels
      routingTable = constructRoutingTable(models)
      rs.foreach { r =>
        collectionActors.getOrElse(r, List.empty).foreach { actor =>
          actor ! ModelOperation.ModelUpdated(r, routingTable(r))
        }
      }
    case PersistentModelStore.DeletedModels(removedModels) =>
      val rs = removedModels.map(m => m.r)
      models = models.diff(removedModels)
      rs.foreach { r =>
        collectionActors.getOrElse(r, List.empty).foreach { actor =>
          context.unwatch(actor)
          actor ! ModelOperation.ModelDeleted(r)
        }
      }
      routingTable = constructRoutingTable(models)
      collectionActors = collectionActors.filterNot(c => rs.contains(c._1))
    case PersistentModelStore.AddedModels(newModels) =>
      models ++= newModels
      routingTable = constructRoutingTable(models)
  }

  def handleCollectionRequest: Receive = {
    case CollectionActorMessage.GetModel(r) =>
      val collectionActor = sender()
      if(validR(r)){
        collectionActors += (r -> (collectionActors.getOrElse(r, Set.empty) + collectionActor))
        context.watch(collectionActor)
        collectionActor ! ModelResult.ModelObject(routingTable(r))
      }
      else collectionActor ! ModelResult.ModelNotFound
  }

  def handleRequest: Receive = {
    case req @ Request.Create(_, r, _) if validR(r) =>
      globals.collectionActor forward req
    case Request.Create(_, r, _) =>
      sender ! ModelRouterResponse.RNotFound(r)
    case req @ Request.Update(_, r, _, _) if validR(r) =>
      globals.collectionActor forward req
    case Request.Update(_, r, _, _) =>
      sender ! ModelRouterResponse.RNotFound(r)
    case req @ Request.Delete(_, r) if validR(r) =>
      globals.collectionActor forward req
    case req @ Request.Delete(_, r) =>
      sender ! ModelRouterResponse.RNotFound(r)
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
