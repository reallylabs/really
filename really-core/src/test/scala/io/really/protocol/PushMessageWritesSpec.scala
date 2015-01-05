/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.protocol

import _root_.io.really._
import play.api.libs.json._
import org.scalatest.{ Matchers, FlatSpec }

class PushMessageWritesSpec extends FlatSpec with Matchers {

  "Create writes" should "write created push message" in {
    val createdObj = Json.obj("name" -> "Salma", "age" -> 24, "_r" -> "/users/12123123133/", "_rev" -> 1)
    val msg = ProtocolFormats.PushMessageWrites.Created.toJson(
      R("/users/"),
      createdObj
    )

    assertResult(Json.obj(
      "r" -> "/users/*/",
      "evt" -> "created",
      "body" -> createdObj
    ))(msg)
  }

  "Delete writes" should "write deleted push message" in {
    val msg = ProtocolFormats.PushMessageWrites.Deleted.toJson(R("/users/1213329889789/"))

    assertResult(Json.obj(
      "r" -> R("/users/1213329889789/"),
      "evt" -> "deleted"
    ))(msg)
  }

  "Update writes" should "write updated push message" in {
    val msg = ProtocolFormats.PushMessageWrites.Updated.toJson(
      R("/users/131232344/"),
      24,
      List(
        FieldUpdatedOp("name", UpdateCommand.Set, Some(JsString("Ahmed")))
      )
    )

    assertResult(Json.obj(
      "r" -> R("/users/131232344/"),
      "rev" -> 24,
      "evt" -> "updated",
      "body" -> Json.obj(
        "name" -> Json.obj(
          "op" -> "set",
          "opValue" -> "Ahmed"
        )
      )
    ))(msg)
  }

}