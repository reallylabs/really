/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io

import io.really.R
import io.really.protocol.ProtocolError.Error
import play.api.libs.json._

object Errors {
  private[this] def formatProtocolError(code: Int, msg: String): JsValue =
    Json.obj("error" -> Error(code, msg, None))

  private[this] def formatJsonError(tag: Long, r: R, code: Int, msg: String, e: JsError): JsValue =
    toJson(tag, Some(r), Error(code, msg, Some(e)))

  private[this] def formatJsonError(tag: Long, code: Int, msg: String, e: JsError): JsValue =
    toJson(tag, None, Error(code, msg, Some(e)))

  private[this] def formatError(tag: Long, code: Int, msg: String): JsValue =
    toJson(tag, None, Error(code, msg, None))

  private[this] def formatError(tag: Long, r: R, code: Int, msg: String): JsValue =
    toJson(tag, Some(r), Error(code, msg, None))

  def toJson(tag: Long, r: Option[R], error: Error): JsValue =
    Json.obj(
      "tag" -> tag,
      "r" -> r,
      "error" -> error
    )

  val badJson: JsValue = formatProtocolError(400, "json.malformed")

  val socketIsIdle: JsValue = formatProtocolError(408, "socket.idle")

  def invalidCommandWhileUninitialized(tag: Long, cmd: String): JsValue =
    formatError(tag, 400, "cannot handle this command while uninitialized")

  def invalidAccessToken(tag: Long): JsValue =
    formatError(tag, 401, "invalid/incorrect/expired access token")

  def invalidInitialize(e: JsError): JsValue =
    Json.obj(
      "error" -> Error(400, "invalid initialize request", Some(e))
    )

}