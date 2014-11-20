/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import _root_.io.really._
import _root_.io.really.model.Model
import akka.persistence.{ PersistentActor, SnapshotOffer }

class PersistentModelStore(globals: ReallyGlobals) extends PersistentActor {
  import PersistentModelStore._

  override def persistenceId = "model-registry-persistent"

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
    case SnapshotOffer(_, snapshot: List[Model @unchecked]) => updateState(snapshot)
  }

  def receiveCommand: Receive = {
    case UpdateModels(models) =>
      val deletedModels = state.filterNot(m => models.map(_.r).contains(m.r))
      val addedModels = models.filterNot(m => state.map(_.r).contains(m.r))
      val changedModels = getChangedModels(models, state)
      if (!changedModels.isEmpty)
        persist(UpdatedModels(changedModels))(_ => updateModels(changedModels))
      if (!deletedModels.isEmpty)
        persist(DeletedModels(deletedModels))(_ => deleteModels(deletedModels))
      if (!addedModels.isEmpty)
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
