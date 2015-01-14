package io.really.io.socket

import akka.actor._
import _root_.io.really._
import _root_.io.really.io.{ AccessTokenInfo, IOGlobals }
import _root_.io.really.protocol.Protocol
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.{ Session, RequestHeader }
import play.api.mvc.RequestHeader
import scala.concurrent.duration._
import scala.util.control.NonFatal
import _root_.io.really.jwt._
import _root_.io.really.protocol.ProtocolFormats.RequestReads._

class WebSocketHandler(
    ioGlobals: IOGlobals,
    coreGlobals: ReallyGlobals,
    header: RequestHeader,
    actorOut: ActorRef
) extends Actor with ActorLogging {

  import _root_.io.really.protocol.ProtocolFormats.CommandErrorWrites._

  override def preStart(): Unit = {
    log.info("WebSocket connection established with {}", header.remoteAddress)
    context.setReceiveTimeout(ioGlobals.config.io.websocketIdleTimeout)
    super.preStart()
  }

  private[socket] def asJsObject(msg: String): Option[JsObject] = {
    try {
      Some(Json.parse(msg).as[JsObject])
    } catch {
      case NonFatal(e) =>
        reply(CommandError.BadJson, None)
        //let's die!
        context.stop(self)
        None
    }
  }

  private[socket] def decodeAccessToken(tag: Long, token: String): Either[CommandError, (FiniteDuration, UserInfo)] = {
    JWT.decode(token, Some(ioGlobals.config.io.accessTokenSecret)) match {
      case JWTResult.JWT(header, payload) =>
        (payload \ "expires").asOpt[Long] match {
          case Some(exp) if exp > DateTime.now().getMillis || exp == 0l =>
            payload.validate(UserInfo.fmt) match {
              case JsSuccess(tokenInfo, _) =>
                val duration = (exp - DateTime.now().getMillis).millis
                Right((duration, tokenInfo))
              case _ => Left(CommandError.InvalidAccessToken)
            }
          case _ => Left(CommandError.ExpiredAccessToken)
        }
      case e => Left(CommandError.InvalidAccessToken)
    }
  }

  private[socket] def handleInitializeRequest(jsObj: JsObject): Unit = {
    import _root_.io.really.protocol.ProtocolFormats.RequestReads._
    jsObj.validate((tagReads and traceIdReads and cmdReads and accessTokenReads).tupled) match {
      case JsSuccess((tag, _, cmd, accessToken), _) if cmd.toLowerCase == "initialize" =>
        decodeAccessToken(tag, accessToken) match {
          case Right((expiresIn, authInfo)) =>
            push(Protocol.initialized(tag, authInfo))
            context.become(
              initializedReceive(expiresIn, authInfo) orElse idleReceive
            )
          case Left(e) =>
            reply(e, Some(tag))
        }
      case JsSuccess((tag, traceId, cmd, _), _) =>
        reply(CommandError.InvalidCommandWhileUninitialized, Some(tag))
      case e: JsError =>
        reply(CommandError.InvalidInitialize, None)
    }
  }

  def nonInitialized: Receive = {
    case msg: String =>
      asJsObject(msg).map(handleInitializeRequest)
  }

  def initializedReceive(expiresIn: FiniteDuration, userInfo: UserInfo): Receive = {
    case msg: String =>
      context.system.scheduler.scheduleOnce(expiresIn, self, PoisonPill)(context.dispatcher)
      asJsObject(msg).map { request =>
        request.validate((tagReads and traceIdReads and cmdReads).tupled) match {
          case JsSuccess((tag, traceId, cmd), _) =>
            val pushChannel = Some(actorOut)
            val when = DateTime.now
            val host: String = header.remoteAddress
            val protocol = RequestProtocol.WebSockets
            val meta = RequestMetadata(traceId, when, host, protocol)
            val ctx = RequestContext(tag.toInt, userInfo, meta)
            coreGlobals.receptionist ! Receptionist.DispatchDelegateFor(ctx, cmd, request, actorOut)
          case _ =>
            reply(CommandError.BadJson, None)
        }
      }
  }

  def idleReceive: Receive = {
    case ReceiveTimeout =>
      reply(CommandError.SocketIsIdle, None)
      // let's die
      context.stop(self)
    case e =>
      log.warning("Received a message that I don't understand! : {}", e)

  }

  def push(msg: JsValue): Unit = actorOut ! msg

  def reply(error: CommandError, tag: Option[Long]): Unit =
    tag match {
      case Some(tag) =>
        push(Json.toJson(error).as[JsObject] ++ Json.obj("tag" -> tag))
      case None =>
        push(Json.toJson(error))
    }

  /**
   * default receive is nonInitialized
   * @return
   */
  def receive = nonInitialized orElse idleReceive
}

object WebSocketHandler {
  def props(
    ioGlobals: IOGlobals,
    coreGlobals: ReallyGlobals,
    accessToken: AccessTokenInfo,
    header: RequestHeader
  )(actorOut: ActorRef): Props =
    Props(new WebSocketHandler(ioGlobals, coreGlobals, header, actorOut))
}
