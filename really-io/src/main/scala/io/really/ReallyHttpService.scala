package io.really

import scala.concurrent.duration._
import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.util.ByteString
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.http.HttpRequest
import spray.can.websocket.FrameCommandFailed
import spray.routing._
import spray.http._
import MediaTypes._

object ReallyHttpService {
  def props(implicit globals: ReallyIOGlobals) = Props(new ReallyHttpService)
}

class ReallyHttpService(implicit globals: ReallyIOGlobals) extends Actor with ActorLogging {
  def receive = {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      val serverConnection = sender()
      val conn = context.actorOf(ServiceWorker.props(serverConnection))
      serverConnection ! Http.Register(conn)
  }
}

object ServiceWorker {
  case class Push(msg: String)
  def props(serverConnection: ActorRef) = Props(classOf[ServiceWorker], serverConnection)
}

class ServiceWorker(val serverConnection: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker with HelloWorldRoutes {
  import ServiceWorker._

  override def receive = handshaking orElse restRoutes orElse closeLogic

  val Subscribe = ByteString("subscribe")

  def businessLogic: Receive = {
    case TextFrame(Subscribe) =>
      implicit val ec = context.system.dispatcher
      context.system.scheduler.schedule(50.millisecond, 50.millisecond, self, ServiceWorker.Push("Hey, I am a push message"))

    case msg: TextFrame =>
      sender() ! msg // Echo

    case x: BinaryFrame =>
      sender() ! x

    case Push(msg) => send(TextFrame(msg))

    case x: FrameCommandFailed =>
      log.error("frame command failed", x)
  }

  def restRoutes: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      myRoute
    }
  }
}

// this trait defines our service behavior independently from the service actor
trait HelloWorldRoutes extends HttpService {
  val myRoute =
    path("") {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
              </body>
            </html>
          }
        }
      }
    }
}
