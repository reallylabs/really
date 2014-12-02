/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io.socket

import _root_.io.really.jwt._
import io.really._
import akka.actor.Props
import akka.testkit.TestProbe
import play.api.libs.json._
import play.api.test._
import _root_.io.really.io.{ IOGlobals, IOConfig }

class WebSocketHandlerSpec extends BaseIOActorSpec {
  val client = TestProbe()
  val clientRef = client.ref

  val token = JWT.encode(config.io.accessTokenSecret, Json.obj(), Json.obj(), Some(Algorithm.HS256))
  val fakeRequest = new FakeRequest("POST", "http://www.really.io", new FakeHeaders(), """""".stripMargin)

  val valid_msg =
    s"""{
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken": "$token"
        }"""

  "Web Socket Handler" should "fail to initialize with incorrect msg format" in {
    val wsHandler = system.actorOf(
      Props(
        new WebSocketHandler(
          new IOGlobals(config),
          io.Global.coreGlobals,
          fakeRequest,
          clientRef
        )
      )
    )
    wsHandler ! "{{{}"
    val em = client.expectMsgType[JsValue]
    em \ "error" \ "code" shouldBe JsNumber(400)
    em \ "error" \ "message" shouldBe JsString("json.malformed")

    val probe = TestProbe()
    probe watch wsHandler
    probe.expectTerminated(wsHandler)
  }

  it should "fail to initialize with invalid initialize request" in {
    val handler = system.actorOf(
      Props(
        new WebSocketHandler(
          new IOGlobals(config),
          io.Global.coreGlobals,
          fakeRequest,
          clientRef
        )
      )
    )
    val invalid_initialize_msg =
      s"""{
        "r": "/users/123",
        "tag": "testtag",
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"$token"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsObject]
    em \ "error" \ "code" shouldBe JsNumber(400)
    em \ "error" \ "message" shouldBe JsString("initialize.invalid")
  }

  it should "fail to initialize with invalid command request" in {
    val handler = system.actorOf(
      Props(
        new WebSocketHandler(
          new IOGlobals(config),
          io.Global.coreGlobals,
          fakeRequest,
          clientRef
        )
      )
    )
    val invalid_initialize_msg =
      """{
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"GET",
        "accessToken":"Ac66bf"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsValue]
    em \ "error" \ "code" shouldBe JsNumber(400)
    em \ "error" \ "message" shouldBe JsString("initialize.required")
  }

  it should "fail to initialize with invalid token" in {
    val handler = system.actorOf(
      Props(
        new WebSocketHandler(
          new IOGlobals(config),
          io.Global.coreGlobals,
          fakeRequest,
          clientRef
        )
      )
    )
    val invalid_initialize_msg =
      """{
        "r": "/users/123",
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"Ac66bf"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsValue]
    em \ "error" \ "code" shouldBe JsNumber(401)
    em \ "error" \ "message" shouldBe JsString("token.invalid")
  }

  it should "success to initialize with valid token" in {
    val handler = system.actorOf(
      Props(
        new WebSocketHandler(
          new IOGlobals(config),
          io.Global.coreGlobals,
          fakeRequest,
          clientRef
        )
      )
    )
    val valid_initialize_msg =
      s"""{
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"$token"
        }"""
    handler ! valid_initialize_msg
    val em = client.expectMsgType[JsValue]
    em \ "evt" shouldBe JsString("initialized")

  }
}
