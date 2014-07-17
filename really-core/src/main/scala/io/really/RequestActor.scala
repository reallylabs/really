package io.really

import akka.actor._
import play.api.libs.json._

abstract class RequestActor(context: RequestContext, replyTo: ActorRef, body: JsObject) extends Actor with ActorLogging