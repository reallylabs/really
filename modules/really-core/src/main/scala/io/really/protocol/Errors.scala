/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.protocol

import play.api.libs.json._

object ProtocolError {

  /*
   * Represent Error message
   */
  case class Error(code: Int, message: String, errors: Option[JsError])

  /*
   * Represent Json Format for Error
   */
  object Error {
    implicit val jsErrWrites = new Writes[JsError] {
      override def writes(e: JsError): JsValue = JsError.toFlatJson(e)
    }
    implicit val writes = Json.writes[Error]
  }

}
