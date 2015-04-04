/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import model.persistent.RequestRouter._
import scala.util.{ Success, Failure }
import akka.actor._
import play.api.libs.json._
import _root_.io.really.protocol.ProtocolFormats._

class RequestDelegate(globals: ReallyGlobals, ctx: RequestContext, replyTo: ActorRef, cmd: String, body: JsObject) extends Actor with ActorLogging {

  import CommandErrorWrites._

  def replyWith[T](o: T)(implicit tjs: Writes[T]): Unit = {
    log.debug("Delegate got: {}", o)
    val rep = Json.toJson(o).as[JsObject]
    log.debug("Delegate reply: {}", rep)
    replyWith(rep)
  }

  def replyWith(response: JsObject): Unit = {
    val taggedResponse = response ++ Json.obj("tag" -> ctx.tag)
    replyTo ! taggedResponse
    context.stop(self)
  }

  override def preStart() =
    RequestReads(cmd).map(reads => body.validate(reads(ctx, replyTo))) match {
      case Success(JsSuccess(request, _)) =>
        globals.requestRouter ! request
        context.become(waitingResponseFor(request))
      case Success(reason: JsError) =>
        replyWith(CommandError.ValidationFailed(reason))
      case Failure(e: MatchError) =>
        replyWith(CommandError.InvalidCommand(cmd))
      case Failure(e) =>
        log.error("RequestDelegate encountered unexpected exception: {}", e)
        replyWith(CommandError.InternalServerError.default)
    }

  context.setReceiveTimeout(globals.config.Request.timeoutAfter)

  def receive = {
    case msg =>
      log.warning("""
                     |RequestDelegate received unexpected message in a wrong state.
                     |This should never happen!
                     |Either `preStart` didn't call `become`,
                     |or someone is sending by mistake. Sender was: {},
                     |and message was: {}.""".stripMargin, sender(), msg)
  }

  def waitingResponseFor(request: Request): Receive = {
    case response: Result =>
      import ResponseWrites.resultWrites
      replyWith(response)
    case error: CommandError =>
      replyWith(error)
    case RNotFound(r) =>
      replyWith(CommandError.UnknownR(r))
    case UnsupportedCmd(cmd) =>
      replyWith(CommandError.InvalidCommand(cmd))

    case other =>
      log.warning("RequestDelegate received unknown response due to a coding bug. response was: {} from {}.", other, sender())
      replyWith(CommandError.InternalServerError.default)
  }

}
