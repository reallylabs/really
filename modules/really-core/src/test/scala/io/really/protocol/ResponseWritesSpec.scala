/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.protocol

import akka.testkit.TestProbe
import io.really.Result._
import io.really._
import _root_.io.really.rql.RQLTokens.PaginationToken
import _root_.io.really.rql.RQL.Query.QueryReads
import org.joda.time.DateTime
import org.scalatest.{ Matchers, FlatSpec }
import play.api.libs.json.{ JsNull, JsString, Json }

class ResponseWritesSpec extends BaseActorSpec {

  import ProtocolFormats.ResponseWrites.resultWrites

  "Subscribe writes" should "write subscribe response schema " in {
    val response = SubscribeResult(Set(
      SubscriptionOpResult(R("/users/12131231232/"), Set("name", "age")),
      SubscriptionOpResult(R("/users/121312787632/"), Set("name", "tel"))
    ))

    val obj = Json.toJson(response)

    assertResult(Json.obj(
      "body" -> Json.obj(
        "subscriptions" -> Set(
          Json.obj(
            "r" -> R / 'users / 12131231232L,
            "fields" -> Set("name", "age")
          ),
          Json.obj(
            "r" -> R / 'users / 121312787632L,
            "fields" -> Set("name", "tel")
          )
        )
      )
    ))(obj)
  }

  "Unsubscribe writes" should "write unsubscribe response schema" in {

    val response = UnsubscribeResult(Set(
      R("/users/123213213123/"),
      R("/users/12113435123212/")
    ))

    val obj = Json.toJson(response)

    assertResult(Json.obj(
      "body" -> Json.obj(
        "unsubscriptions" -> Set(
          R / 'users / 123213213123L,
          R / 'users / 12113435123212L
        )
      )
    ))(obj)
  }

  "Get writes" should "write get response schema" in {
    val response = GetResult(R("/users/1123123/"), Json.obj("firstName" -> "Salma", "lastName" -> "Khater"), Set("firstName", "lastName"))

    val obj = Json.toJson(response)

    assertResult(Json.obj(
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
    val response = Result.UpdateResult(R("/users/12345654321/"), 24)
    val obj = Json.toJson(response)

    assertResult(Json.obj(
      "r" -> "/users/12345654321/",
      "rev" -> 24
    ))(obj)
  }

  "Read writes" should "write read response schema" in {
    val request = Request.Read(ctx, R("/users/"), ReadOpts(
      Set("name", "age"),
      QueryReads.reads(Json.obj(
        "filter" -> "name = $name and age > $age",
        "values" -> Json.obj("name" -> "Ahmed", "age" -> 20)
      )).get,
      10,
      false,
      Some(PaginationToken(23423423, 1)),
      0,
      false,
      false
    ), TestProbe().ref)

    val response = ReadResult(
      R("/users/"),
      ReadResponseBody(
        None,
        None,
        List(ReadItem(Json.obj("name" -> "Ahmed", "age" -> 24), Json.obj()))
      ),
      None
    )

    val obj = Json.toJson(response)

    assertResult(Json.obj(
      "r" -> "/users/*/",
      "body" -> Json.obj(
        "items" -> List(Json.obj("body" -> Json.obj("name" -> "Ahmed", "age" -> 24), "meta" -> Json.obj()))
      )
    ))(obj)
  }

  "Create writes" should "write create response schema" in {
    val response = CreateResult(R("/users/"), Json.obj(
      "firstname" -> "Salma",
      "lastname" -> "Khater",
      "_r" -> "/users/129890763222/",
      "_rev" -> 1
    ))

    val obj = Json.toJson(response)

    assertResult(Json.obj(
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
    val response = DeleteResult(R("/users/13432423434/"))

    val obj = Json.toJson(response)

    assertResult(Json.obj("r" -> "/users/13432423434/"))(obj)
  }

}
