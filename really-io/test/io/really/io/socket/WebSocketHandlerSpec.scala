/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io.socket

import io.really._
import akka.actor.Props
import akka.testkit.TestProbe
import play.api.libs.json._
import play.api.test._
import _root_.io.really.io.{ IOGlobals, IOConfig }

import scala.util.parsing.json.JSONObject

class WebSocketHandlerSpec extends BaseIOActorSpec {
  //    println("************************START********************************")
  //    println("************************STOP*********************************")
  val client = TestProbe()
  val clientRef = client.ref
  val fakeRequest = new FakeRequest("POST", "http://www.really.io", new FakeHeaders(), """""".stripMargin)

  val valid_msg =
    """{
        "email": "reallyApp@really.io",
        "r": "/users/123",
        "Application":"reallyApp",
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"Ac66bf"
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
      """{
        "email": "reallyApp@really.io",
        "r": "/users/123",
        "Application":"reallyApp",
        "tag": "testtag",
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"Ac66bf"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsObject]
    em \ "error" \ "code" shouldBe JsNumber(400)
    em \ "error" \ "message" shouldBe JsString("invalid initialize request")
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
        "email": "reallyApp@really.io",
        "r": "/users/123",
        "Application":"reallyApp",
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"GET",
        "accessToken":"Ac66bf"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsValue]
    em \ "error" \ "code" shouldBe JsNumber(400)
    em \ "error" \ "message" shouldBe JsString("cannot handle this command while uninitialized")
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
        "email": "reallyApp@really.io",
        "r": "/users/123",
        "Application":"reallyApp",
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"Ac66bf"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsValue]
    em \ "error" \ "code" shouldBe JsNumber(401)
    em \ "error" \ "message" shouldBe JsString("invalid/incorrect/expired access token")
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
    val invalid_initialize_msg =
      """{
        "email": "reallyApp@really.io",
        "r": "/users/123",
        "Application":"reallyApp",
        "tag": 123,
        "traceId" : "@trace123",
        "cmd":"initialize",
        "accessToken":"eyJhbGciOiJIbWFjU0hBMjU2IiwidHlwIjoiSldUIn0.eyJuYW1lIjoiQWhtZWQiLCJlbWFpbCI6ImFobWVkQGdtYWlsLmNvbSJ9.77-977-977-9ZX1xee-_vQxJKu-_ve-_vQvvv70c77-9OO-_ve-_ve-_ve-_vQfvv71277-9fjhj77-9bw"
        }"""
    handler ! invalid_initialize_msg
    val em = client.expectMsgType[JsValue]
    em \ "evt" shouldBe JsString("initialized")

  }
}
