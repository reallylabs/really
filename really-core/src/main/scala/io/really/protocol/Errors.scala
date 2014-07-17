package io.really.protocol

import io.really._
import play.api.libs.json.{Json, JsObject}


object ProtocolError {

  /*
   * Represent Error message
   */
  case class Error(code: Int, message: String, errors: JsObject)
  /*
   * Represent Json Format for Error
   */
  object Error{
    implicit val fmt = Json.format[Error]
  }

  /*
   * Represent Json writes for error response
   */
  object ErrorResponse{
    def writes(tag: Int, r: R, error: Error) =
      Json.obj(
        "tag" -> tag,
        "r" -> r,
        "error" -> error)
  }

}
