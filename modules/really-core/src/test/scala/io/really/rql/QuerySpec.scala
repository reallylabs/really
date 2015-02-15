/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.rql

import io.really.model.{ DataType, Field, ValueField }
import io.really.rql.RQL.SimpleQuery
import org.scalatest.{ Matchers, FlatSpec }
import play.api.libs.json._

class QuerySpec extends FlatSpec with Matchers {

  "Query" should "return true if object match query" in {
    val fields: Set[Field[_]] = Set(
      ValueField("age", DataType.RLong, None, None, true),
      ValueField("name", DataType.RString, None, None, true)
    )
    val obj = Json.obj("name" -> "Amal", "age" -> 24)
    val q1 = SimpleQuery(RQL.Term("age"), RQL.Operator.Gt, RQL.TermValue(JsNumber(20)))
    q1.validateObject(obj, fields) shouldBe true

    val q2 = SimpleQuery(RQL.Term("name"), RQL.Operator.Eq, RQL.TermValue(JsString("Amal")))
    q2.validateObject(obj, fields) shouldBe true

    val q3 = SimpleQuery(RQL.Term("age"), RQL.Operator.IN, RQL.TermValue(JsArray(Seq(JsNumber(24), JsNumber(30)))))
    q3.validateObject(obj, fields) shouldBe true

    val q4 = SimpleQuery(RQL.Term("age"), RQL.Operator.Between, RQL.TermValue(JsArray(Seq(JsNumber(20), JsNumber(40)))))
    q4.validateObject(obj, fields) shouldBe true
  }

  it should "be return false if object doesn't match query" in {
    val fields: Set[Field[_]] = Set(
      ValueField("age", DataType.RLong, None, None, true),
      ValueField("name", DataType.RString, None, None, true)
    )
    val obj = Json.obj("name" -> "Amal", "age" -> 27)
    val q1 = SimpleQuery(RQL.Term("age"), RQL.Operator.Lt, RQL.TermValue(JsNumber(20)))
    q1.validateObject(obj, fields) shouldBe false

    val q2 = SimpleQuery(RQL.Term("name"), RQL.Operator.Eq, RQL.TermValue(JsString("Ahmed")))
    q2.validateObject(obj, fields) shouldBe false

    val q3 = SimpleQuery(RQL.Term("age"), RQL.Operator.IN, RQL.TermValue(JsArray(Seq(JsNumber(20), JsNumber(30)))))
    q3.validateObject(obj, fields) shouldBe false

    val q4 = SimpleQuery(RQL.Term("age"), RQL.Operator.Between, RQL.TermValue(JsArray(Seq(JsNumber(10), JsNumber(20)))))
    q4.validateObject(obj, fields) shouldBe false
  }

}