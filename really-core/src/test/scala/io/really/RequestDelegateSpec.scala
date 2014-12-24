/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ PoisonPill, Props, ActorRef }
import akka.testkit.{ TestActorRef, TestProbe }
import akka.pattern.ask
import play.api.libs.json._
import _root_.io.really.protocol.{ GetOpts }

class RequestDelegateSpec extends BaseActorSpec {

  override val globals = new TestReallyGlobals(config, system) {
    override lazy val requestRouter = self
  }

  "Request Delegate" should "send validation error to client, if request is bad" in {
    val cmd = "get"
    val body = Json.obj("r" -> R / 'users / 123) // cmdOpts is missing
    val client = TestProbe()
    val delegate = system.actorOf(globals.requestProps(ctx, client.ref, cmd, body))
    expectNoMsg()
    client.expectMsg(Json.obj(
      "tag" -> ctx.tag,
      "r" -> JsNull,
      "error" -> Json.obj(
        "code" -> 409,
        "message" -> "validation.failed",
        "errors" -> Json.obj(
          "obj.cmdOpts" -> Seq(Json.obj(
            "msg" -> "error.path.missing",
            "args" -> Seq.empty[String]
          ))
        )
      )
    ))
  }

  it should "send invalid command error to client, if cmd is wrong" in {
    val wrongCmd = "got"
    val body = Json.obj(
      "r" -> R / 'users / 123,
      "cmdOpts" -> Json.obj(
        "fields" -> Seq("name")
      )
    )
    val client = TestProbe()
    val delegate = system.actorOf(globals.requestProps(ctx, client.ref, wrongCmd, body))
    expectNoMsg()
    client.expectMsg(Json.obj(
      "tag" -> ctx.tag,
      "r" -> JsNull,
      "error" -> Json.obj(
        "code" -> 454,
        "message" -> "Invalid command: got"
      )
    ))
  }

  it should "parse request into AST and forward to request router" in {
    val cmd = "get"
    val body = Json.obj(
      "r" -> R / 'users / 123,
      "cmdOpts" -> Json.obj(
        "fields" -> Seq("name")
      )
    )
    val client = TestProbe()
    val delegate = system.actorOf(globals.requestProps(ctx, client.ref, cmd, body))
    expectMsg(Request.Get(ctx, R / 'users / 123, GetOpts(Set("name"))))
  }

  it should "parse the response back to the client" in {
    val cmd = "get"
    val body = Json.obj(
      "r" -> R / 'users / 123,
      "cmdOpts" -> Json.obj(
        "fields" -> Seq("name")
      )
    )
    val client = TestProbe()
    val delegate = system.actorOf(globals.requestProps(ctx, client.ref, cmd, body))

    delegate ! Result.GetResult(R / 'users / 123, Json.obj(
      "r" -> R / 'users / 123,
      "name" -> "Tamer Abdul-Radi"
    ), Set("name"))

    client.expectMsg(Json.obj(
      "tag" -> 1,
      "r" -> R / 'users / 123,
      "meta" -> Json.obj("fields" -> Seq("name")),
      "body" -> Json.obj("r" -> R / 'users / 123, "name" -> "Tamer Abdul-Radi")
    ))
  }

  it should "parse the command errors and send back to the client" in {
    val cmd = "get"
    val body = Json.obj(
      "r" -> R / 'users / 123,
      "cmdOpts" -> Json.obj(
        "fields" -> Seq("name")
      )
    )
    val client = TestProbe()
    val delegate = system.actorOf(globals.requestProps(ctx, client.ref, cmd, body))

    delegate ! CommandError.ObjectNotFound(R / 'users / 123)

    client.expectMsg(Json.obj(
      "tag" -> 1,
      "r" -> R / 'users / 123,
      "error" -> Json.obj("code" -> 404, "message" -> "object.missing")
    ))
  }

  it should "report internal server error to client if received unexpected reponse" in {
    val cmd = "get"
    val body = Json.obj(
      "r" -> R / 'users / 123,
      "cmdOpts" -> Json.obj(
        "fields" -> Seq("name")
      )
    )
    val client = TestProbe()
    val delegate = system.actorOf(globals.requestProps(ctx, client.ref, cmd, body))

    delegate ! "unexpected reponse!"

    client.expectMsg(Json.obj(
      "tag" -> ctx.tag,
      "r" -> JsNull,
      "error" -> Json.obj(
        "code" -> 500,
        "message" -> "internal.server.error"
      )
    ))
  }

}
