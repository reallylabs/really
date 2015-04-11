/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.rql.writes.mongo

import io.really.rql.RQL.Operator._
import io.really.rql.RQL._
import play.api.libs.json._

object MongoWrites {

  /*
  * Represent implicit format for Operator
  */
  implicit object OperatorFmt extends Writes[Operator] {

    def writes(o: Operator): JsString = o match {
      case Gt => JsString("$gt")
      case Gte => JsString("$gte")
      case Lt => JsString("$lt")
      case Lte => JsString("$lte")
      case IN => JsString("$in")
      case _ => JsString("")
    }
  }

  object SimpleQueryWrites extends Writes[SimpleQuery] {
    def writes(sq: SimpleQuery): JsObject = sq.op match {
      case Eq =>
        Json.obj(
          sq.key.term -> sq.termValue.value
        )
      case Between =>
        val value = sq.termValue.value.as[Seq[JsNumber]]
        Json.obj(
          sq.key.term -> Json.obj("$gte" -> value(0), "$lte" -> value(1))
        )
      case _ =>
        Json.obj(
          sq.key.term -> Json.obj(
            Json.toJson(sq.op).as[JsString].value -> sq.termValue.value
          )
        )
    }
  }

  object AndCombinatorWrites extends Writes[AndCombinator] {

    import QueryWrites._

    def writes(aq: AndCombinator): JsObject = (aq.q1, aq.q2) match {
      case (q1: SimpleQuery, q2: SimpleQuery) if q1.key.term == q2.key.term && (q1.op == Eq || q2.op == Eq) =>
        Json.obj(
          "$and" -> Json.arr(Json.toJson(aq.q1), Json.toJson(aq.q2))
        )
      case (q1: SimpleQuery, q2: SimpleQuery) if q1.key.term == q2.key.term =>
        Json.obj(
          q1.key.term -> Json.obj(
            Json.toJson(q1.op).as[JsString].value -> q1.termValue.value,
            Json.toJson(q2.op).as[JsString].value -> q2.termValue.value
          )
        )
      case (q1: SimpleQuery, q2: SimpleQuery) =>
        Json.toJson(q1).as[JsObject] ++ Json.toJson(q2).as[JsObject]
      case _ =>
        Json.obj(
          "$and" -> Json.arr(Json.toJson(aq.q1), Json.toJson(aq.q2))
        )
    }
  }

  object AndExcludeDeletedWrites extends Writes[AndExcludeDeleted] {

    import QueryWrites._

    def writes(ae: AndExcludeDeleted): JsObject = {
      val exclude = Json.obj("_deleted" -> Json.obj("$exists" -> 0))
      ae.query match {
        case EmptyQuery => exclude
        case q => Json.obj(
          "$and" -> Json.arr(Json.toJson(q), exclude)
        )
      }
    }
  }

  /*
   * JSON Writes for Query
   */
  implicit object QueryWrites extends Writes[Query] {

    def writes(q: Query): JsObject = q match {
      case EmptyQuery => Json.obj()

      case sq: SimpleQuery => Json.toJson(sq)(SimpleQueryWrites).as[JsObject]
      case aq: AndCombinator => Json.toJson(aq)(AndCombinatorWrites).as[JsObject]
      case ae: AndExcludeDeleted => Json.toJson(ae)(AndExcludeDeletedWrites).as[JsObject]

    }
  }

}