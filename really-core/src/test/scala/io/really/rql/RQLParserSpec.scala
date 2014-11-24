/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.rql

import io.really.rql.RQL.{ AndCombinator, SimpleQuery }
import org.scalatest.{ Matchers, FlatSpec }
import play.api.libs.json.{ JsArray, JsNumber, Json, JsString }

class RQLParserSpec extends FlatSpec with Matchers {
  "RQLParser" should "not parse incorrect operators" in {
    val error = intercept[RQL.ParseError] {
      val q = "age on $age"
      val v = Json.obj("age" -> 30)
      RQLParser.parse(q, v)
    }
    error.getMessage.endsWith("expected supported operator") shouldBe true
  }

  it should "not parse if there is invalid field name" in {
    val error = intercept[RQL.ParseError] {
      val q = "1age = $age"
      val v = Json.obj("age" -> 30)
      RQLParser.parse(q, v)
    }
    error.getMessage.endsWith("expected fieldName with valid format") shouldBe true
  }

  it should "not parse if there is any missing value" in {
    intercept[RQL.MissingValue] {
      val q = "age = $age"
      val v = Json.obj("test" -> 30)
      RQLParser.parse(q, v)
    }
  }

  it should "not parse if there is missing 'AND'" in {
    val error = intercept[RQL.ParseError] {
      val q = "age = $age name = $name"
      val v = Json.obj("age" -> 30)
      RQLParser.parse(q, v)
    }
  }

  it should "parse '=' operator" in {
    val q = "name = $name"
    val v = Json.obj("name" -> "amal")
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("name")
    query.op should equal(RQL.Operator.Eq)
    query.value.value should equal(JsString("amal"))
  }

  it should "parse '<' operator" in {
    val q = "age < $age1"
    val v = Json.obj("age1" -> 20)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("age")
    query.op should equal(RQL.Operator.Lt)
    query.value.value should equal(JsNumber(20))
  }

  it should "parse '>' operator" in {
    val q = "age > $age1"
    val v = Json.obj("age1" -> 20)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("age")
    query.op should equal(RQL.Operator.Gt)
    query.value.value should equal(JsNumber(20))
  }

  it should "parse '<=' operator" in {
    val q = "age <= $age1"
    val v = Json.obj("age1" -> 20)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("age")
    query.op should equal(RQL.Operator.Lte)
    query.value.value should equal(JsNumber(20))
  }

  it should "parse '>=' operator" in {
    val q = "age >= $age1"
    val v = Json.obj("age1" -> 20)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("age")
    query.op should equal(RQL.Operator.Gte)
    query.value.value should equal(JsNumber(20))
  }

  it should "parse 'in' operator" in {
    val q = "name in $names"
    val names = JsArray(Seq(JsString("amal"), JsString("ahmed")))
    val v = Json.obj("names" -> names)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("name")
    query.op should equal(RQL.Operator.IN)
    query.value.value should equal(names)
  }

  it should "parse 'IN' operator" in {
    val q = "name IN $names"
    val names = JsArray(Seq(JsString("amal"), JsString("ahmed")))
    val v = Json.obj("names" -> names)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = result.asInstanceOf[SimpleQuery]
    query.key.term should equal("name")
    query.op should equal(RQL.Operator.IN)
    query.value.value should equal(names)
  }

  it should "parse 'and' combinator" in {
    val q = "name >= $name and age = $age"
    val v = Json.obj("name" -> "amal", "age" -> 55)
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[RQL.AndCombinator] shouldBe true
    val andC = result.asInstanceOf[RQL.AndCombinator]

    andC.q1.isInstanceOf[SimpleQuery] shouldBe true
    val q1 = andC.q1.asInstanceOf[SimpleQuery]

    q1.key.term should equal("name")
    q1.op should equal(RQL.Operator.Gte)
    q1.value.value should equal(JsString("amal"))

    andC.q2.isInstanceOf[SimpleQuery] shouldBe true
    val q2 = andC.q2.asInstanceOf[SimpleQuery]

    q2.key.term should equal("age")
    q2.op should equal(RQL.Operator.Eq)
    q2.value.value should equal(JsNumber(55))

  }

  it should "parse nested 'and' combinator" in {
    val q = "firstName = $name1 and lastName = $name2 and age < $age"
    val v = Json.obj("name1" -> "amal", "name2" -> "ahmed", "age" -> 30)
    val result = RQLParser.parse(q, v)

    result.isInstanceOf[AndCombinator] shouldBe true
    val firstAnd = result.asInstanceOf[AndCombinator]

    firstAnd.q1.isInstanceOf[AndCombinator] shouldBe true
    val q1 = firstAnd.q1.asInstanceOf[AndCombinator]
    val n1 = q1.q1.asInstanceOf[SimpleQuery]

    n1.key.term should equal("firstName")
    n1.op should equal(RQL.Operator.Eq)
    n1.value.value should equal(JsString("amal"))

    val n2 = q1.q2.asInstanceOf[SimpleQuery]

    n2.key.term should equal("lastName")
    n2.op should equal(RQL.Operator.Eq)
    n2.value.value should equal(JsString("ahmed"))

    firstAnd.q2 shouldBe a[SimpleQuery]
    val q4 = firstAnd.q2.asInstanceOf[SimpleQuery]

    q4.key.term should equal("age")
    q4.op should equal(RQL.Operator.Lt)
    q4.value.value should equal(JsNumber(30))

  }

}