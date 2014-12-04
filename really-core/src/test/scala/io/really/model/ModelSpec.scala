/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really.R
import io.really.model.ModelExceptions.{ InvalidSubCollectionR, InvalidCollectionR }
import org.scalatest.{ Matchers, FlatSpec }

class ModelSpec extends FlatSpec with Matchers {
  "Model" should "raise exception if the passed R is not a collection R" in {
    val r = R / "users" / 123
    val collMeta: CollectionMetadata = CollectionMetadata(1L)
    val nameField = ValueField("name", DataType.RString, None, None, true)
    val ageField = ValueField("age", DataType.RLong, None, None, true)
    val fields: Map[FieldKey, Field[_]] = Map("name" -> nameField, "age" -> ageField)
    val thrown = intercept[InvalidCollectionR] {
      Model(r, collMeta, fields,
        JsHooks(
          Some(""),
          None,
          None,
          None,
          None,
          None,
          None
        ), null, List.empty)
    }
    thrown.getMessage shouldBe s"Invalid collection R: $r"
  }

  it should "optionally accept list of sub collections with correct Rs" in {
    val collMeta: CollectionMetadata = CollectionMetadata(1L)
    val nameField = ValueField("name", DataType.RString, None, None, true)
    val ageField = ValueField("age", DataType.RLong, None, None, true)
    val fields: Map[FieldKey, Field[_]] = Map("name" -> nameField, "age" -> ageField)

    val accountsR = R / "users" / "accounts"

    val boardsR = R / "users" / "boards"

    val subCollections = List(accountsR, boardsR)

    val usersR = R / "users"
    Model(usersR, collMeta, fields,
      JsHooks(
        Some(""),
        None,
        None,
        None,
        None,
        None,
        None
      ), null, subCollections)
  }

  it should "refuse to create a model with any sub collection its R is not a direct child of the model under creation" in {
    val collMeta: CollectionMetadata = CollectionMetadata(1L)
    val nameField = ValueField("name", DataType.RString, None, None, true)
    val ageField = ValueField("age", DataType.RLong, None, None, true)
    val fields: Map[FieldKey, Field[_]] = Map("name" -> nameField, "age" -> ageField)

    val accountsR = R / "users" / "profiles" / "accounts"

    val boardsR = R / "users" / "boards"

    val subCollections = List(accountsR, boardsR)

    val usersR = R / "users"
    val thrown = intercept[InvalidSubCollectionR] {
      Model(usersR, collMeta, fields,
        JsHooks(
          Some(""),
          None,
          None,
          None,
          None,
          None,
          None
        ), null, subCollections)
    }
    thrown.getMessage shouldBe s"Invalid sub collection R: $accountsR"
  }

}
