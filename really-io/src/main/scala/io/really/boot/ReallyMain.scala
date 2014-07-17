package io.really.boot

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp

import io.really._

class ReallyApp {
  val config = new ReallyConfig(ConfigFactory.load) with IOConfig
  implicit val globals: ReallyIOGlobals = new DefaultReallyIOGlobals(config)
  globals.boot()

  implicit val system = ActorSystem("really-io")
  implicit val ec = system.dispatcher
  import globals._

  val server = system.actorOf(ReallyHttpService.props, "really-http-service")

  IO(UHttp) ! Http.Bind(server, config.IO.host, config.IO.port)

  def shutdown() = {
    system.shutdown()
    globals.actorSystem.shutdown()
  }

  def awaitTermination() = {
    system.awaitTermination()
    globals.actorSystem.awaitTermination()
  }
}

object ReallyMain extends App {
  val app = new ReallyApp()

  println("""
  ____            _ _
 |  _ \ ___  __ _| | |_   _
 | |_) / _ \/ _` | | | | | |
 |  _ <  __/ (_| | | | |_| |
 |_| \_\___|\__,_|_|_|\__, |
                      |___/
  """)

  readLine(" ***  Press any key to exit.  *** ")
  app.shutdown()
  app.awaitTermination()
  println("Have a nice day.")
}
