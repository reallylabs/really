/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import java.util.concurrent.ThreadLocalRandom
import scala.util.Random
import akka.actor._
import play.api.libs.json.JsObject
import akka.actor.SupervisorStrategy._

/**
 * Receives a [[Request]] instance and a requester [[akka.actor.ActorRef]] and instantiates a RequestActor
 * that serves this request and dies upon finalising the request
 *
 * The Receptionist is a single actor per machine and is restarted on death (based on default
 * supervision strategy)
 *
 */
class Receptionist(global: ReallyGlobals) extends Actor with ActorLogging {
  import Receptionist._
  type ReplyToActor = ActorRef
  type DelegateActor = ActorRef
  //  var inFlight = Map.empty[DelegateActor, ReplyToActor]
  override val supervisorStrategy = OneForOneStrategy() {
    case e: Exception =>
      log.error("Delegate crashed {} we are not restarting it", e)
      Stop
  }

  def currentTime = System.currentTimeMillis
  val epoch = currentTime

  def duration = currentTime - epoch
  var messagesCount = BigInt(0)

  def frequency = BigDecimal(messagesCount) / BigDecimal(duration)

  def random = new Random(ThreadLocalRandom.current)

  def randomString = random.alphanumeric take 10 mkString ""

  def delegateName(ctx: RequestContext) =
    s"${ctx.auth.authType}-${ctx.auth.uid}-${ctx.tag}-$randomString"

  def newDelegate(ctx: RequestContext, replyTo: ActorRef, cmd: String, body: JsObject): ActorRef = {
    val delegate = context.actorOf(global.requestProps(ctx, replyTo, cmd, body), delegateName(ctx))
    context.watch(delegate)
    //    inFlight += (delegate -> replyTo)
    delegate
  }

  def receive = {
    case DispatchDelegateFor(ctx, cmd, body, pushChannel) =>
      log.debug("Receptionist received request: {} {} {}", ctx, cmd, body)
      newDelegate(ctx, pushChannel, cmd, body)
      messagesCount += 1
    case GetMetrics =>
      sender() ! Metrics(messagesCount, frequency)
    case Terminated(actor) =>
      log.debug("Delegate {} terminated", actor)

    case msg =>
      log.debug("Receptionist received unhandled message: {}", msg)
  }
}

object Receptionist {
  case class DispatchDelegateFor(ctx: RequestContext, cmd: String, body: JsObject, pushChannel: ActorRef)
  case object GetMetrics
  case class Metrics(messagesCount: BigInt, frequency: BigDecimal)
}
