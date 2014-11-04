/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import play.api.libs.json.{ JsObject }
import _root_.io.really._

trait Command

case object Ping extends Command //for testing purposes only

//State Definition
case class ModelObject(data: JsObject, modelVersion: ModelVersion, lastTouched: Map[FieldKey, Revision])

trait ModelHookStatus

object ModelHookStatus {

  case class Terminated(code: Int, message: String) extends ModelHookStatus

  case object Succeeded extends ModelHookStatus

  case class JSValidationError(terminated: Terminated) extends Error
}
