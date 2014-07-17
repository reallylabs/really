package io.really

import io.backchat.hookup._
import io.really.testutil._

class HelloWorldWSRoutesSpec extends WSSpec("HelloWorldWSRoutesSpec") {
  "WebSocket API" should "respond with echo messages" in {
    client.send("Hello")
    expectMsg(TextMessage("Hello"))

    client.send("subscribe")
    expectMsg(TextMessage("Hey, I am a push message"))
    expectMsg(TextMessage("Hey, I am a push message"))
    expectMsg(TextMessage("Hey, I am a push message"))
    expectMsg(TextMessage("Hey, I am a push message")) // Stream is infinite
    client.close()
  }
}
