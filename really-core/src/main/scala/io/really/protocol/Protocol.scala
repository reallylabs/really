/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.protocol

import io.really.{ UserInfo, R, Revision }
import play.api.libs.json._

object Protocol {
  import ProtocolError.Error //exports the error implicit writer
  import Event._
  import UserInfo.fmt
  private[protocol] def writeMessage(
    tag: Option[Long] = None,
    evt: Option[Event] = None,
    meta: Option[JsObject] = None,
    r: Option[R] = None,
    rev: Option[Revision] = None,
    body: Option[JsObject] = None,
    error: Option[Error] = None
  ): JsValue =
    Json.obj(
      "tag" -> tag,
      "meta" -> meta,
      "r" -> r,
      "rev" -> rev,
      "evt" -> evt,
      "body" -> body,
      "error" -> error
    )

  def initialized(tag: Long, userInfo: UserInfo): JsValue =
    writeMessage(
      tag = Some(tag),
      evt = Some(Event.Initialized),
      body = Some(Json.toJson(userInfo).as[JsObject])
    )

}