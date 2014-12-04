/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.protocol

import play.api.libs.json._

abstract class Event(ev: String) {
  override def toString() = ev
}

object Event {
  implicit object EventWrites extends Writes[Event] {
    def writes(e: Event): JsValue = JsString(e.toString)
  }

  object Initialized extends Event("initialized")
}