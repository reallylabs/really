/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import akka.actor._
import play.api.libs.json._

class RequestDelegate(globals: ReallyGlobals, ctx: RequestContext, replyTo: ActorRef, cmd: String, body: JsObject) extends Actor with ActorLogging {
  def receive = Actor.emptyBehavior
}
