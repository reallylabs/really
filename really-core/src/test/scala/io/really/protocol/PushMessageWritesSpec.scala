/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.protocol

import io.really._
import play.api.libs.json._
import org.scalatest.{Matchers, FlatSpec}


class PushMessageWritesSpec extends FlatSpec with Matchers {

  "Create writes" should "write created push message" in {
    val createdObj = Json.obj("name" -> "Salma", "age" -> 24, "_r" -> "/users/12123123133/" , "_rev" -> 1)
    val msg = ProtocolFormats.PushMessageWrites.Created.write(
      "subscriptionID",
      R("/users/"),
      createdObj)

    assertResult(Json.obj(
      "r" -> "/users/*/",
      "evt" -> "created",
      "meta" -> Json.obj("subscription" -> "subscriptionID"),
      "body" -> createdObj))(msg)
  }

  "Delete writes" should "write deleted push message" in {
    val msg = ProtocolFormats.PushMessageWrites.Deleted.write(R("/users/1213329889789/"), R("/friends/1231231232/"))

    assertResult(Json.obj(
      "r" -> R("/friends/1231231232/"),
      "evt" -> "deleted",
      "meta" -> Json.obj("deletedBy" -> R("/users/1213329889789/"))))(msg)
  }

  "Update writes" should "write updated push message" in {
    val msg = ProtocolFormats.PushMessageWrites.Updated.write(
      R("/users/131232344/"),
      24,
      List(
        FieldUpdatedOp("name", UpdateCommand.Set, Some(JsString("Ahmed")), R("/users/131232344/"))))

    assertResult(Json.obj(
      "r" -> R("/users/131232344/"),
      "rev" -> 24,
      "evt" -> "updated",
      "body" -> Json.obj(
        "name" -> Json.obj(
          "op" -> "set",
          "opValue" -> "Ahmed",
          "opBy" -> R("/users/131232344/")))))(msg)
  }

}
