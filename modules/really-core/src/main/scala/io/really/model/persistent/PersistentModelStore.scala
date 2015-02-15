/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import _root_.io.really._
import _root_.io.really.model.Model
import akka.actor.ActorLogging
import akka.persistence.{ PersistentActor, SnapshotOffer }

class PersistentModelStore(globals: ReallyGlobals, persistId: String) extends PersistentActor with ActorLogging {
  import PersistentModelStore._

  log.info("Persistent Model Store Started")
  override def persistenceId = persistId

  protected var state: Models = List.empty

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
    case UpdatedModels(models) =>
      log.info("loading saved data models from journal")
      updateModels(models)
    case DeletedModels(models) => deleteModels(models)
    case AddedModels(models) => addModels(models)
    case SnapshotOffer(_, snapshot: List[Model @unchecked]) =>
      log.info("loading saved data model snapshot from sanpshot-store")
      updateState(snapshot)
  }

  def receiveCommand: Receive = {
    case UpdateModels(models) =>
      log.info("Updating data models...")
      val deletedModels = state.filterNot(m => models.map(_.r).contains(m.r))
      val addedModels = models.filterNot(m => state.map(_.r).contains(m.r))
      val changedModels = getChangedModels(models, state)
      log.info("------Model Update Summary------")
      if (changedModels.nonEmpty) {
        log.info("Models updated:" + changedModels)
        persist(UpdatedModels(changedModels))(_ => updateModels(changedModels))
      } else log.info("No models has been modified!")
      if (deletedModels.nonEmpty) {
        log.info("Models that will be deleted:" + deletedModels)
        persist(DeletedModels(deletedModels))(_ => deleteModels(deletedModels))
      } else log.info("No models to be deleted!")
      if (addedModels.nonEmpty) {
        log.info("Models that will be added:" + addedModels)
        persist(AddedModels(addedModels))(_ => addModels(addedModels))
      } else log.info("No new models to be added!")
      log.info("--------------------------------")
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
