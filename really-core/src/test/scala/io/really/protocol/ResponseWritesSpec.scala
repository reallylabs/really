/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.protocol

import io.really.Result._
import io.really._
import org.joda.time.DateTime
import org.scalatest.{ Matchers, FlatSpec }
import play.api.libs.json.{ JsNull, JsNumber, JsString, Json }

class ResponseWritesSpec extends FlatSpec with Matchers {
  val ctx = RequestContext(
    1,
    UserInfo(AuthProvider.Anonymous, R("/_anonymous/1234567"), Application("reallyApp")),
    None, RequestMetadata(None, DateTime.now, "localhost", RequestProtocol.WebSockets)
  )

  "Subscribe writes" should "write subscribe response schema " in {
    val request = Request.Subscribe(ctx, SubscriptionBody(
      List(
        SubscriptionOp(R("/users/12131231232/"), 2, Set("name", "age")),
        SubscriptionOp(R("/users/121312787632/"), 2, Set.empty)
      )
    ))

    val response = SubscribeResult(Set(
      SubscriptionOpResult(R("/users/12131231232/"), Set("name", "age")),
      SubscriptionOpResult(R("/users/121312787632/"), Set.empty[String])
    ))

    val obj = ProtocolFormats.ResponseWrites.Subscribe.toJson(request, response)

    assertResult(Json.obj(
      "tag" -> ctx.tag,
      "body" -> Json.obj(
        "subscriptions" -> Set(
          Json.obj("r" -> "/users/12131231232/", "fields" -> Set("name", "age")),
          Json.obj("r" -> "/users/121312787632/", "fields" -> Set.empty[String])
        )
      )
    ))(obj)
  }

  "Unsubscribe writes" should "write unsubscribe response schema" in {
    val request = Request.Unsubscribe(ctx, UnsubscriptionBody(
      List(
        UnsubscriptionOp(R("/users/123213213123/"), Set("name", "age")),
        UnsubscriptionOp(R("/users/12113435123212/"), Set.empty)
      )
    ))

    val response = UnsubscribeResult(Set(
      SubscriptionOpResult(R("/users/123213213123/"), Set("name", "age")),
      SubscriptionOpResult(R("/users/12113435123212/"), Set.empty)
    ))

    val obj = ProtocolFormats.ResponseWrites.Unsubscribe.toJson(request, response)

    assertResult(Json.obj(
      "tag" -> ctx.tag,
      "body" -> Json.obj(
        "unsubscriptions" -> Set(
          Json.obj("r" -> "/users/123213213123/", "fields" -> Set("name", "age")),
          Json.obj("r" -> "/users/12113435123212/", "fields" -> Set.empty[String])
        )
      )
    ))(obj)
  }

  "Get Subscription writes" should "write get-subscription response schema" in {
    val request = Request.GetSubscription(ctx, R("/users/1123123/"))

    val response = GetSubscriptionResult(Set("name"))

    val obj = ProtocolFormats.ResponseWrites.GetSubscription.toJson(request, response)

    assertResult(Json.obj("tag" -> ctx.tag, "r" -> "/users/1123123/", "body" -> Json.obj("fields" -> Set("name"))))(obj)
  }

  "Get writes" should "write get response schema" in {
    val request = Request.Get(ctx, R("/users/1123123/"), GetOpts(Set("firstName", "lastName")))

    val response = GetResult(Json.obj("firstName" -> "Salma", "lastName" -> "Khater"), Set("firstName", "lastName"))

    val obj = ProtocolFormats.ResponseWrites.Get.toJson(request, response)

    assertResult(Json.obj(
      "tag" -> ctx.tag,
      "meta" ->
        Json.obj("fields" -> Set("firstName", "lastName")),
      "r" -> "/users/1123123/",
      "body" -> Json.obj("firstName" -> "Salma", "lastName" -> "Khater")
    ))(obj)
  }

  "Update writes" should "write update response schema" in {
    val request = Request.Update(
      ctx,
      R("/users/12345654321/"),
      23,
      UpdateBody(List(
        UpdateOp(UpdateCommand.Set, "firstName", JsString("Ahmed")),
        UpdateOp(UpdateCommand.Set, "lastName", JsString("Mahmoud"))
      ))
    )
    val response = Result.UpdateResult(24)
    val obj = ProtocolFormats.ResponseWrites.Update.toJson(request, response)

    assertResult(Json.obj(
      "tag" -> ctx.tag,
      "r" -> "/users/12345654321/",
      "rev" -> 24
    ))(obj)
  }

  "Read writes" should "write read response schema" in {
    val request = Request.Read(ctx, R("/users/"), ReadOpts(
      Set("name", "age"),
      Json.obj("filter" -> "name = {1} and age > {2}", "values" -> List(JsString("Ahmed"), JsNumber(20))),
      10,
      "-r",
      "23423423:1",
      0,
      false,
      false
    ))

    val response = ReadResult(
      ReadResponseBody(
        None,
        None,
        List(ReadItem(Json.obj("name" -> "Ahmed", "age" -> 24), Json.obj()))
      ),
      None
    )

    val obj = ProtocolFormats.ResponseWrites.Read.toJson(request, response)

    assertResult(Json.obj(
      "tag" -> ctx.tag,
      "meta" -> Json.obj("subscription" -> JsNull),
      "r" -> "/users/*/",
      "body" -> Json.obj(
        "items" -> List(Json.obj("body" -> Json.obj("name" -> "Ahmed", "age" -> 24), "meta" -> Json.obj()))
      )
    ))(obj)
  }

  "Create writes" should "write create response schema" in {
    val request = Request.Create(ctx, R("/users/"), Json.obj(
      "firstname" -> "Salma",
      "lastname" -> "Khater"
    ))

    val response = CreateResult(Json.obj(
      "firstname" -> "Salma",
      "lastname" -> "Khater",
      "_r" -> "/users/129890763222/",
      "_rev" -> 1
    ))

    val obj = ProtocolFormats.ResponseWrites.Create.toJson(request, response)

    assertResult(Json.obj(
      "tag" -> ctx.tag,
      "r" -> "/users/*/",
      "body" -> Json.obj(
        "firstname" -> "Salma",
        "lastname" -> "Khater",
        "_r" -> "/users/129890763222/",
        "_rev" -> 1
      )
    ))(obj)
  }

  "Delete writes" should "write delete response schema" in {
    val request = Request.Delete(ctx, R("/users/13432423434/"))

    val response = DeleteResult

    val obj = ProtocolFormats.ResponseWrites.Delete.toJson(request)

    assertResult(Json.obj("tag" -> ctx.tag, "r" -> "/users/13432423434/"))(obj)
  }

}