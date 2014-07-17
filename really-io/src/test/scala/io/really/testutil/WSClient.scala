package io.really.testutil

import scala.concurrent.Await
import io.backchat.hookup._
import java.net.URI
import akka.actor.ActorRef
import org.jboss.netty.logging.{ InternalLogger, InternalLoggerFactory, JdkLoggerFactory}
import scala.concurrent.duration._

class WSClient(uri: URI)(implicit forwardTo: ActorRef) {
  val client = new DefaultHookupClient(HookupClientConfig(uri)) {
    val connection = connect()
    def receive = {
      case any => forwardTo ! any
    }
  }
  def send(msg: String) = client.send(msg)
  def reconnect() = Await.ready(client.reconnect(), 30.seconds)
  def close() = client.close()
}

object WSClient {
  def apply(uri: String)(implicit forwardTo: ActorRef) = new WSClient(new URI(uri))
  /*
  // Keep the client silent, necessary if we want to re-try until connect to application
  private val silentLogger = new InternalLogger {
    def debug(x$1: String,x$2: Throwable): Unit = {}
    def debug(x$1: String): Unit = {}
    def error(x$1: String): Unit = {}
    def error(x$1: String,x$2: Throwable): Unit = {}
    def info(x$1: String,x$2: Throwable): Unit = {}
    def info(x$1: String): Unit = {}
    def isDebugEnabled(): Boolean = false
    def isEnabled(x$1: org.jboss.netty.logging.InternalLogLevel): Boolean = false
    def isErrorEnabled(): Boolean = false
    def isInfoEnabled(): Boolean = false
    def isWarnEnabled(): Boolean = false
    def log(x$1: org.jboss.netty.logging.InternalLogLevel,x$2: String,x$3: Throwable): Unit = {}
    def log(x$1: org.jboss.netty.logging.InternalLogLevel,x$2: String): Unit = {}
    def warn(x$1: String,x$2: Throwable): Unit = {}
    def warn(x$1: String): Unit = {}
  }
  InternalLoggerFactory.setDefaultFactory(new JdkLoggerFactory {
    override def newInstance(name: String): InternalLogger =
      if (name == "HookupClient") silentLogger
      else super.newInstance(name)
  })*/
}
