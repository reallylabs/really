package io.really.protocol

import org.joda.time.DateTime
import org.scalatest.{Matchers, FlatSpec}
import play.api.libs.json._
import io.really._


class RequestReadsSpec extends FlatSpec with Matchers {
  val ctx = RequestContext(1, AuthInfo.Anonymous, None, RequestMetadata(None, DateTime.now, "localhost", RequestProtocol.WebSockets))

  "Subscribe request reads" should "create subscribe request if you sent correct request" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "subscribe",
      "body" -> Json.obj(
        "subscriptions" -> List(
          Json.obj("r" -> "/users/12131231232/", "rev" -> 2, "fields" -> Set("name", "age")),
          Json.obj("r" -> "/users/121312787632/", "rev" -> 2, "fields" -> Set.empty[String])
        )
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Subscribe.read(ctx))

    assertResult(Request.Subscribe(ctx,
      SubscriptionBody(
        List(SubscriptionOp(R("/users/12131231232/"), 2, Set("name", "age")),
          SubscriptionOp(R("/users/121312787632/"), 2, Set.empty)))))(result.get)
  }

  it should "return JsError if you don't sent subscription list" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "subscribe",
      "body" -> Json.obj())

    val result = req.validate(ProtocolFormats.RequestReads.Subscribe.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you sent subscrptionOp without rev" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "subscribe",
      "body" -> Json.obj(
        "subscriptions" -> List(
          Json.obj("r" -> "/users/12131231232/", "fields" -> Set("name", "age"))
        )
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Subscribe.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you sent subscrptionOp with invalid R" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "subscribe",
      "body" -> Json.obj(
        "subscriptions" -> List(
          Json.obj("r" -> "users/", "rev" -> 3, "fields" -> Set("name", "age"))
        )
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Subscribe.read(ctx))

    assert(result.isError == true)
  }

  "Unsubscribe request reads" should "create unsubscribe request if you sent correct request" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "unsubscribe",
      "body" -> Json.obj(
        "subscriptions" -> List(
          Json.obj("r" -> "/users/12131231232/", "fields" -> Set("name", "age")),
          Json.obj("r" -> "/users/121312787632/", "fields" -> Set.empty[String])
        )
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Unsubscribe.read(ctx))

    assertResult(Request.Unsubscribe(ctx,
      UnsubscriptionBody(
        List(UnsubscriptionOp(R("/users/12131231232/"), Set("name", "age")),
          UnsubscriptionOp(R("/users/121312787632/"), Set.empty)))))(result.get)
  }

  it should "return JsError if you don't sent subscription list" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "unsubscribe",
      "body" -> Json.obj())

    val result = req.validate(ProtocolFormats.RequestReads.Unsubscribe.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you sent unsubscrptionOp with invalid R" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "unsubscribe",
      "body" -> Json.obj(
        "subscriptions" -> List(
          Json.obj("r" -> "users/", "fields" -> Set("name", "age"))
        )
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Unsubscribe.read(ctx))

    assert(result.isError == true)
  }

  "Get Subscription request read" should "create Get Subscription request if you sent correct request" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "get-subscription",
      "r" -> "/users/1123123/")

    val result = req.validate(ProtocolFormats.RequestReads.GetSubscription.read(ctx))

    assertResult(Request.GetSubscription(ctx, R("/users/1123123/")))(result.get)
  }

  it should "return JsError if you sent request without r" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "get-subscription")

    val result = req.validate(ProtocolFormats.RequestReads.GetSubscription.read(ctx))

    assert(result.isError == true)
  }

  "Get request read" should "create Get request if you sent correct request" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "get",
      "r" -> "/users/1123123/",
      "cmdOpts" -> Json.obj(
        "fields" -> Set("firstname", "lastname")
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Get.read(ctx))

    assertResult(Request.Get(ctx, R("/users/1123123/"), GetOpts(Set("firstname", "lastname"))))(result.get)
  }

  it should "create Get request if you sent fields as empty set" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "get",
      "r" -> "/users/1123123/",
      "cmdOpts" -> Json.obj(
        "fields" -> Set.empty[String]
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Get.read(ctx))

    assertResult(Request.Get(ctx, R("/users/1123123/"), GetOpts(Set())))(result.get)
  }

  it should "return JsError if you didn't sent r" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "get",
      "cmdOpts" -> Json.obj(
        "fields" -> Set.empty[String]
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Get.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you didn't sent fields" in {
    val req = Json.obj("tag" -> 1,
      "cmd" -> "get",
      "r" -> "/users/1123123/",
      "cmdOpts" -> Json.obj())

    val result = req.validate(ProtocolFormats.RequestReads.Get.read(ctx))

    assert(result.isError == true)
  }

  "Update Request read" should "create update request if you sent correct request" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "update",
      "cmdOpts" -> Json.obj("transaction" -> true),
      "r" -> "/users/12345654321/",
      "rev" -> 23,
      "body" -> Json.obj(
        "ops" -> List(
          Json.obj("op" -> "set", "key" -> "firstname", "value" -> "Ahmed"),
          Json.obj("op" -> "set", "key" -> "lastname", "value" -> "Mahmoud"))))

    val result = req.validate(ProtocolFormats.RequestReads.Update.read(ctx))

    assertResult(Request.Update(
      ctx,
      R("/users/12345654321/"),
      23,
      UpdateBody(List(
        UpdateOp(UpdateCommand.Set, "firstname", JsString("Ahmed")),
        UpdateOp(UpdateCommand.Set, "lastname", JsString("Mahmoud"))
      ))))(result.get)
  }

  it should "return JsError if you sent request without r" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "update",
      "cmdOpts" -> Json.obj("transaction" -> true),
      "rev" -> 23,
      "body" -> Json.obj(
        "ops" -> List(
          Json.obj("op" -> "set", "key" -> "firstname", "value" -> "Ahmed"),
          Json.obj("op" -> "set", "key" -> "lastname", "value" -> "Mahmoud"))))

    val result = req.validate(ProtocolFormats.RequestReads.Update.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you sent request without rev" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "update",
      "cmdOpts" -> Json.obj("transaction" -> true),
      "r" -> "/users/12345654321/",
      "body" -> Json.obj(
        "ops" -> List(
          Json.obj("op" -> "set", "key" -> "firstname", "value" -> "Ahmed"),
          Json.obj("op" -> "set", "key" -> "lastname", "value" -> "Mahmoud"))))

    val result = req.validate(ProtocolFormats.RequestReads.Update.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you sent request without ops" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "update",
      "cmdOpts" -> Json.obj("transaction" -> true),
      "r" -> "/users/12345654321/",
      "rev" -> 23,
      "body" -> Json.obj())

    val result = req.validate(ProtocolFormats.RequestReads.Update.read(ctx))
    assert(result.isError == true)
  }

  it should "return JsError if you sent request with ops but empty list" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "update",
      "cmdOpts" -> Json.obj("transaction" -> true),
      "r" -> "/users/12345654321/",
      "rev" -> 23,
      "body" -> Json.obj(
        "ops" -> List()))

    val result = req.validate(ProtocolFormats.RequestReads.Update.read(ctx))
    assert(result.isError == true)
  }


  "Delete request read" should "create delete request if you sent correct request" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "delete",
      "r" -> "/users/13432423434"
    )

    val result = req.validate(ProtocolFormats.RequestReads.Delete.read(ctx))

    assertResult(Request.Delete(ctx, R("/users/13432423434")))(result.get)
  }

  it should "return JsError if you sent request without r" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "delete"
    )

    val result = req.validate(ProtocolFormats.RequestReads.Delete.read(ctx))

    assert(result.isError == true)
  }

  "Create Request read" should "return create request if you sent correct request" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "create",
      "r" -> "/users/",
      "body" -> Json.obj(
        "firstname" -> "Salma",
        "lastname" -> "Khater"
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Create.read(ctx))

    assertResult(Request.Create(ctx, R("/users/"), Json.obj(
      "firstname" -> "Salma",
      "lastname" -> "Khater"
    )))(result.get)
  }

  it should "return JsError if you sent request without r" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "create",
      "body" -> Json.obj(
        "firstname" -> "Salma",
        "lastname" -> "Khater"
      ))

    val result = req.validate(ProtocolFormats.RequestReads.Create.read(ctx))

    assert(result.isError == true)
  }

  it should "return JsError if you sent request without body" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "create",
      "r" -> "/users/")

    val result = req.validate(ProtocolFormats.RequestReads.Create.read(ctx))

    assert(result.isError == true)
  }

  "Read Request reads" should "create read request if you sent correct request" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "read",
      "r" -> "/users/",
      "cmdOpts" -> Json.obj(
        "fields" -> Set("name", "age"),
        "query" -> Json.obj(
          "filter" -> "name = {1} and age > {2}",
          "values" -> List(JsString("Ahmed"), JsNumber(20))
        ),
        "limit" -> 10,
        "sort" -> "-r",
        "paginationToken" -> "23423423:1",
        "skip" -> 0,
        "includeTotalCount" -> false,
        "subscribe" -> false
      )
    )

    val result = req.validate(ProtocolFormats.RequestReads.Read.read(ctx))

    assertResult(Request.Read(ctx, R("/users/"), ReadOpts(
      Set("name", "age"),
      Json.obj("filter" -> "name = {1} and age > {2}", "values" -> List(JsString("Ahmed"), JsNumber(20))),
      10,
      "-r",
      "23423423:1",
      0,
      false,
      false
    )))(result.get)
  }

  it should "return JsError if you sent request without r" in {
    val req = Json.obj(
      "tag" -> 1,
      "cmd" -> "read",
      "cmdOpts" -> Json.obj(
        "fields" -> Set("name", "age"),
        "query" -> Json.obj(
          "filter" -> "name = {1} and age > {2}",
          "values" -> List(JsString("Ahmed"), JsNumber(20))
        ),
        "limit" -> 10,
        "sort" -> "-r",
        "paginationToken" -> "23423423:1",
        "skip" -> 0,
        "includeTotalCount" -> false,
        "subscribe" -> false
      )
    )

    val result = req.validate(ProtocolFormats.RequestReads.Read.read(ctx))

    assert(result.isError == true)
  }

}
