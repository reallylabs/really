package io.really.io.socket

import akka.actor._
import _root_.io.really._
import _root_.io.really.io.{ AccessTokenInfo, Errors, IOGlobals }
import _root_.io.really.protocol.{ Protocol, ProtocolFormats }
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.{ Session, RequestHeader }
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import _root_.io.really.jwt._
import _root_.io.really.protocol.ProtocolFormats.RequestReads._

class WebSocketHandler(
    ioGlobals: IOGlobals,
    coreGlobals: ReallyGlobals,
    header: RequestHeader,
    actorOut: ActorRef
) extends Actor with ActorLogging {

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
        push(Errors.badJson)
        //let's die!
        context.stop(self)
        None
    }
  }

  private[socket] def decodeAccessToken(tag: Long, token: String): Option[(Duration, UserInfo, AppId, JsObject)] = {
    JWT.decode(token, Some(ioGlobals.config.io.accessTokenSecret)) match {
      case JWTResult.JWT(header, payload) =>
        log.info("Token contents:" + payload)
        //todo: check expiry?
        val expiresIn = Duration.Inf
        //todo: extract UserInfo
        val userInfo = UserInfo(AuthProvider.Anonymous, R("/_anonymous/911"), Application("SampleApp"))
        val appId = "MyApp" //todo: fix me
        Some((expiresIn, userInfo, appId, payload))
      case e =>
        None
    }
  }

  private[socket] def handleInitializeRequest(jsObj: JsObject): Unit = {
    import _root_.io.really.protocol.ProtocolFormats.RequestReads._
    jsObj.validate((tagReads and traceIdReads and cmdReads and accessTokenReads).tupled) match {
      case JsSuccess((tag, _, cmd, accessToken), _) if cmd.toLowerCase == "initialize" =>
        decodeAccessToken(tag, accessToken) match {
          case Some((expiresIn, authInfo, appId, tokenBody)) =>
            push(Protocol.initialized(tag, authInfo))
            context.become(
              initializedReceive(expiresIn, authInfo, appId, tokenBody) orElse idleReceive
            )
          case None =>
            push(Errors.invalidAccessToken(tag))
        }
      case JsSuccess((tag, traceId, cmd, _), _) =>
        push(Errors.invalidCommandWhileUninitialized(tag, cmd))
      case e: JsError =>
        push(Errors.invalidInitialize(e))
    }
  }

  def nonInitialized: Receive = {
    case msg: String =>
      asJsObject(msg).map(handleInitializeRequest)
  }

  def initializedReceive(expiresIn: Duration, userInfo: UserInfo, appId: AppId, token: JsObject): Receive = {
    case msg: String =>
      asJsObject(msg).map { request =>
        request.validate((tagReads and traceIdReads and cmdReads).tupled) match {
          case JsSuccess((tag, traceId, cmd), _) =>
            val pushChannel = Some(actorOut)
            val when = DateTime.now
            val host: String = header.remoteAddress
            val protocol = RequestProtocol.WebSockets
            val meta = RequestMetadata(traceId, when, host, protocol)
            val ctx = RequestContext(tag, userInfo, pushChannel, meta)
            coreGlobals.receptionist ! Receptionist.DispatchDelegateFor(ctx, cmd, request)
          case _ =>
            push(Errors.badJson)
        }
      }
  }

  def idleReceive: Receive = {
    case ReceiveTimeout =>
      push(Errors.socketIsIdle)
      // let's die
      context.stop(self)
    case e =>
      log.warning("Received a message that I don't understand! : {}", e)

  }

  def push(msg: JsValue): Unit = actorOut ! msg

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
