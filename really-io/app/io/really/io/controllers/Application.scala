/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io.controllers

import io.really.io.socket.WebSocketHandler
import play.api.libs.json.{ Json, JsValue }
import play.api.mvc._

import scala.concurrent.Future
import io.really.io.{ Global, AccessTokenInfo }
import javax.script._

object Application extends Controller {
  import play.api.Play.current

  def extractAccessToken(req: RequestHeader): Option[AccessTokenInfo] = {
    val header: Option[String] =
      req.headers.get("Authorization")
        .filter(_.startsWith("Bearer"))
        .map(t => t.substring(6))
        .orElse(req.queryString.get("access_token").map(_.head))
    header.map(token => AccessTokenInfo(Json.obj("token" -> token)))
  }

  def encodeError(status: Application.Status, reason: String): Result =
    status.apply(Json.obj("code" -> status.header.status, "reason" -> reason)).as("application/json")

  def index = Action {
    Ok("Howdy!")
  }

  def socket(protocolVersion: String) = WebSocket.tryAcceptWithActor[String, JsValue] {
    request =>
      val maybeAccessToken = extractAccessToken(request)
      maybeAccessToken match {
        case Some(tokenInfo) =>
          //let's process
          //validate the version
          if (protocolVersion != "v0.1")
            Future.successful(Left(encodeError(BadRequest, "Protocol Version Is Not Supported!")))
          else Future.successful(Right(WebSocketHandler.props(Global.ioGlobals, Global.coreGlobals, tokenInfo, request)))
        case None => Future.successful(Left(encodeError(Forbidden, "Access Token Missing!")))
      }
  }

}