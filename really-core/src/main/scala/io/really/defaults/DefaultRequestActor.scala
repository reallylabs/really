/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.defaults

import akka.actor.{Actor, ActorRef}
import io.really.{RequestActor, RequestContext}
import play.api.libs.json.JsObject

class DefaultRequestActor(context: RequestContext, replyTo: ActorRef, body: JsObject) extends RequestActor(context: RequestContext, replyTo: ActorRef, body: JsObject) {
  def receive = Actor.emptyBehavior
}