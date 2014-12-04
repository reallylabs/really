/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.rql.writes.mongo

import io.really.rql.RQL._
import io.really.rql.{ RQLParser, RQL }
import io.really.rql.RQL.Query.QueryReads
import org.scalatest.{ Matchers, FlatSpec }
import play.api.libs.json._

class MongoWritesSpec extends FlatSpec with Matchers {
  import MongoWrites._

  "Operator" should "write mongo expressions as JSString" in {
    Json.toJson(RQL.Operator.Gt) shouldBe JsString("$gt")
    Json.toJson(RQL.Operator.Gte) shouldBe JsString("$gte")
    Json.toJson(RQL.Operator.Lt) shouldBe JsString("$lt")
    Json.toJson(RQL.Operator.Lte) shouldBe JsString("$lte")
    Json.toJson(RQL.Operator.Eq) shouldBe JsString("$eq")
    Json.toJson(RQL.Operator.IN) shouldBe JsString("$in")
    Json.toJson(RQL.Operator.Between) shouldBe JsString("$???")
  }

  "Simple Query" should "writes key value in case of Equal operation" in {
    val q = "name = $name"
    val v = Json.obj("name" -> "amal")
    val result = RQLParser.parse(q, v)
    result.isInstanceOf[SimpleQuery] shouldBe true
    val query = SimpleQuery(Term("name"), Operator.Eq, TermValue(JsString("amal")))
    val js = Json.toJson(query)
    js.toString() shouldEqual """{"name":"amal"}"""
  }
  it should "writes object with operator if differ than Eq" in {
    val query = SimpleQuery(Term("age"), Operator.Gte, TermValue(JsNumber(20)))
    val js = Json.toJson(query)
    js.toString() shouldEqual """{"age":{"$gte":20}}"""
  }

  "AndCombinator Query" should "write query with Operator (Gte, Lt) like Mongo AND Condition in same term" in {
    val qc1 = SimpleQuery(Term("age"), Operator.Gte, TermValue(JsNumber(20)))
    val qc2 = SimpleQuery(Term("age"), Operator.Lt, TermValue(JsNumber(40)))
    val qJson = Json.toJson(AndCombinator(qc1, qc2))
    qJson shouldEqual Json.obj("age" -> Json.obj("$gte" -> 20, "$lt" -> 40))
  }

  it should "write query with Operator (Eq, Lt) like Mongo AND Condition in same term" in {
    val qc1 = SimpleQuery(Term("age"), Operator.Eq, TermValue(JsNumber(20)))
    val qc2 = SimpleQuery(Term("age"), Operator.Lt, TermValue(JsNumber(40)))
    val qJson = Json.toJson(AndCombinator(qc1, qc2))
    qJson shouldEqual Json.obj("$and" -> Json.arr(Json.obj("age" -> 20), Json.obj("age" -> Json.obj("$lt" -> 40))))
  }

  it should "write query with Operator (Eq, Eq) like Mongo AND Condition in different term" in {
    val qc1 = SimpleQuery(Term("weight"), Operator.Eq, TermValue(JsNumber(80)))
    val qc2 = SimpleQuery(Term("age"), Operator.Eq, TermValue(JsNumber(40)))
    val qJson = Json.toJson(AndCombinator(qc1, qc2))
    qJson shouldEqual Json.obj("weight" -> 80, "age" -> 40)
  }

  it should "write query with Operator (Gte, Lt) like Mongo AND Condition in different term" in {
    val qc1 = SimpleQuery(Term("age"), Operator.Gte, TermValue(JsNumber(20)))
    val qc2 = SimpleQuery(Term("weight"), Operator.Lt, TermValue(JsNumber(80)))
    val qJson = Json.toJson(AndCombinator(qc1, qc2))
    qJson shouldEqual Json.obj("age" -> Json.obj("$gte" -> 20), "weight" -> Json.obj("$lt" -> 80))
  }

  it should "write complex query" in {
    val qc1 = SimpleQuery(Term("age"), Operator.Gte, TermValue(JsNumber(20)))
    val qc2 = SimpleQuery(Term("age"), Operator.Lt, TermValue(JsNumber(40)))
    val qc3 = SimpleQuery(Term("weight"), Operator.Lt, TermValue(JsNumber(80)))
    val qJson = Json.toJson(AndCombinator(AndCombinator(qc1, qc2), qc3))
    qJson shouldEqual Json.obj("$and" -> Json.arr(Json.obj("age" -> Json.obj("$gte" -> 20, "$lt" -> 40)), Json.obj("weight" -> Json.obj("$lt" -> 80))))
  }

  "Query Readers" should "parse request values correctly" in {
    val queryRequest = Json.obj(
      "filter" -> "firstName = $name1 and lastName = $name2 and age < $age",
      "values" -> Json.obj("name1" -> "amal", "name2" -> "ahmed", "age" -> 30)
    )

    val result = queryRequest.validate[Query]
    result.isSuccess shouldBe true

    val query = Json.fromJson[Query](queryRequest).get
    query.isInstanceOf[AndCombinator] shouldBe true
  }

  it should "parse simple query request values correctly" in {
    val queryRequest = Json.obj(
      "filter" -> "firstName = $name1",
      "values" -> Json.obj("name1" -> "amal")
    )

    val result = queryRequest.validate[Query]
    result.isSuccess shouldBe true

    val query = Json.fromJson[Query](queryRequest).get
    query.isInstanceOf[SimpleQuery] shouldBe true
  }

  it should "raise error if values is missing" in {
    val queryRequest = Json.obj(
      "filter" -> "firstName = $name1 and lastName = $name2 and age < $age",
      "value" -> Json.obj("name1" -> "amal", "name2" -> "ahmed", "age" -> 30)
    )

    val result = queryRequest.validate[Query]
    result.isError shouldBe true
  }

  it should "raise error if filter is missing" in {
    val queryRequest = Json.obj(
      "query" -> "firstName = $name1 and lastName = $name2 and age < $age",
      "values" -> Json.obj("name1" -> "amal", "name2" -> "ahmed", "age" -> 30)
    )

    val result = queryRequest.validate[Query]
    result.isError shouldBe true
  }

  it should "raise error if request not a JsObject" in {
    val queryRequest = JsString("fake request")
    val result = queryRequest.validate[Query]
    result.isError shouldBe true
    val err = result.asInstanceOf[JsError]

  }

}