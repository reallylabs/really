package io.really.model

import play.api.libs.json.JsObject
import io.really._


trait Command

case class Create(ctx: RequestContext, obj: JsObject) extends Command
case object Ping extends Command //for testing purposes only
case object GetState extends Command //for testing purposes only

//todo: add CreateOpts


trait Event

case class Created(obj: JsObject, modelVersion: ModelVersion) extends Event


//State Definition
case class ModelObject(data: JsObject, modelVersion: ModelVersion) {
  def updateState(evt: Event, modelVersion: ModelVersion): ModelObject = this
}

trait CommandResponse
case class AlreadyExists(r: R) extends CommandResponse
case class ObjectCreated(obj: JsObject) extends CommandResponse
case class ValidationFailed(reason: String) extends CommandResponse
case class State(obj: JsObject, modelVersion: ModelVersion) extends CommandResponse //for testing purposes only
//  case object CommandSuccessful extends CommandResponse
//  case class CommandFailed(reason: String) extends CommandResponse

trait ModelHookStatus

object ModelHookStatus {

  case class Terminated(code: Int, message: String) extends ModelHookStatus

  case object Succeeded extends ModelHookStatus

  case class ValidationError(terminated: Terminated) extends Error
}
