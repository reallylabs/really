package io.really.model

import io.really._
import akka.persistence.{SnapshotOffer, PersistentActor}

class PersistentModelStore(globals: ReallyGlobals) extends PersistentActor {
  import PersistentModelStore._

  override def persistenceId = "persistent-model"

  var state: Models = List.empty

  private def updateState(models: Models): Unit =
    state = models

  private def addModels(models: Models): Unit =
    state ++= models

  private def updateModels(models: Models): Unit = {
    val rs = models.map(m => m.r)
    state = state.filter(m => !rs.contains(m.r)) ++ models
  }

  private def deleteModels(models: Models): Unit =
    state = state.diff(models)

  def getChangedModels(newModels: Models, oldModels: Models): Models = {
    val intersectRs = newModels.map(_.r) intersect oldModels.map(_.r)
    newModels.filter(m => intersectRs.contains(m.r)) diff oldModels.filter(m => intersectRs.contains(m.r))
  }

  def receiveRecover: Receive = {
    case UpdatedModels(models) => updateModels(models)
    case DeletedModels(models) => deleteModels(models)
    case AddedModels(models) => addModels(models)
    case SnapshotOffer(_, snapshot: Models) => updateState(snapshot)
  }

  def receiveCommand: Receive = {
    case UpdateModels(models) =>
      val deletedModels = state.diff(models)
      val addedModels = models.diff(state)
      val changedModels = getChangedModels(models, state)
      if(!changedModels.isEmpty)
        persist(UpdatedModels(changedModels))(_ => updateModels(changedModels))
      if(!deletedModels.isEmpty)
        persist(DeletedModels(deletedModels))(_ => deleteModels(deletedModels))
      if(!addedModels.isEmpty)
        persist(AddedModels(addedModels))(_ => addModels(addedModels))
  }

}

object PersistentModelStore {
  type Models = List[Model]

  trait Command
  case class UpdateModels(models: Models) extends Command

  trait Event
  case class UpdatedModels(models: Models) extends Event
  case class DeletedModels(models: Models) extends Event
  case class AddedModels(models: Models) extends Event
}
