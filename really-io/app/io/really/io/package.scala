/**
 * Copyright (C) 2014 Really Inc. <http://really.io>
 */

package io.really.io

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.json.JsObject
import _root_.io.really.ReallyConfig

case class AccessTokenInfo(obj: JsObject)

trait IOConfig {
  this: ReallyConfig =>

  reallyConfig.checkValid(reference, "io")
  val ioConfig = reallyConfig.getConfig("io")

  object io {
    val websocketIdleTimeout = ioConfig.getDuration("websocket.idle-timeout", TimeUnit.MILLISECONDS).millis
    val accessTokenSecret = ioConfig.getString("security.secret-key")
  }

}