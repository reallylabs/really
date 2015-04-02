/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import _root_.io.really.rql.RQL.Query
import _root_.io.really.rql.RQLTokens.PaginationToken
import _root_.io.really.model.FieldKey
import _root_.io.really.rql.RQL
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json._

package object protocol {
  /*
   * Represents request options on get request
   */
  case class GetOpts(fields: Set[FieldKey] = Set.empty)

  /*
   * Represent implicit JSON Format for GetOpts
   */
  object GetOpts {
    implicit val fmt = Json.format[GetOpts]
  }

  /**
   * Represents request options on read request
   */
  case class ReadOpts(
    fields: Set[FieldKey],
    query: Query,
    limit: Int,
    ascending: Boolean = false,
    paginationToken: Option[PaginationToken] = None,
    skip: Int = 0,
    includeTotalCount: Boolean = false,
    subscribe: Boolean = false
  )

  /*
   * Represent implicit JSON Format for ReadOpts
   */
  object ReadOpts {

    //    implicit val reads = Json.reads[ReadOpts]
    implicit val reads = (
      (__ \ 'fields).readNullable[Set[FieldKey]].defaultsTo(Set.empty) and
      (__ \ 'query).readNullable[Query].defaultsTo(RQL.EmptyQuery) and
      (__ \ 'limit).readNullable[Int].defaultsTo(DEFAULT_QUERY_LIMIT) and
      (__ \ 'ascending).readNullable[Boolean].defaultsTo(false) and
      (__ \ 'paginationToken).readNullable[PaginationToken] and
      (__ \ 'skip).readNullable[Int].defaultsTo(0) and
      (__ \ 'includeTotalCount).readNullable[Boolean].defaultsTo(false) and
      (__ \ 'subscribe).readNullable[Boolean].defaultsTo(false)
    )(ReadOpts.apply _)
  }

  sealed trait UpdateCommand

  /*
   * Represent Update Operation
   */
  object UpdateCommand {

    case object AddNumber extends UpdateCommand

    case object Push extends UpdateCommand

    case object Pull extends UpdateCommand

    case object Set extends UpdateCommand

    case object AddToSet extends UpdateCommand

    case object RemoveAt extends UpdateCommand

    case object InsertAt extends UpdateCommand

  }

  /*
   * Represent implicit format for Update operation
   */
  implicit object UpdateOperationFmt extends Format[UpdateCommand] {

    import UpdateCommand._

    def reads(json: JsValue) = json match {
      case JsString("addNumber") => JsSuccess(AddNumber)
      //      case JsString("push") => JsSuccess(Push)
      //      case JsString("pull") => JsSuccess(Pull)
      case JsString("set") => JsSuccess(Set)
      //      case JsString("addToSet") => JsSuccess(AddToSet)
      //      case JsString("insertAt") => JsSuccess(InsertAt)
      //      case JsString("removeAt") => JsSuccess(RemoveAt)
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.unsupported.command"))))
    }

    def writes(o: UpdateCommand): JsString = o match {
      case AddNumber => JsString("addNumber")
      //      case Push => JsString("push")
      //      case Pull => JsString("pull")
      case Set => JsString("set")
      //      case AddToSet => JsString("addToSet")
      //      case RemoveAt => JsString("removeAt")
      //      case InsertAt => JsString("insertAt")
    }
  }

  /*
   * Represents operation on update request
   */
  case class UpdateOp(op: UpdateCommand, key: FieldKey, value: JsValue, opArgs: Option[JsObject] = None)

  /*
   * Represent implicit JSON Format for UpdateOp
   */
  object UpdateOp {
    implicit val fmt = Json.format[UpdateOp]
  }

  /*
   * Represent body request for update request
   */
  case class UpdateBody(ops: List[UpdateOp])

  /*
   * Represent implicit JSON Format for UpdateBody
   */
  object UpdateBody {
    val write = Json.writes[UpdateBody]
    val read: Reads[UpdateBody] = Json.reads[UpdateBody].filterNot(ValidationError("validate.error.unexpected.value", UpdateBody(List.empty)))(_ == UpdateBody(List.empty))
    implicit val fmt = Format[UpdateBody](read, write)
  }

  /*
   * Represents subscription operation for one object on subscribe request
   */
  case class SubscriptionOp(r: R, rev: Revision, fields: Set[FieldKey] = Set.empty)

  /*
   * Represent implicit JSON Format for SubscriptionOp
   */
  object SubscriptionOp {
    implicit val fmt = Json.format[SubscriptionOp]
  }

  /*
   * Represent body request for subscribe request
   */
  case class SubscriptionBody(subscriptions: List[SubscriptionOp])

  /*
   * Represent implicit JSON Format for SubscriptionBody
   */
  object SubscriptionBody {
    implicit val fmt = Json.format[SubscriptionBody]
  }

  /*
   * Represent body request for unsubscribe request
   */
  case class UnsubscriptionBody(subscriptions: List[R])

  /*
   * Represent implicit JSON Format for SubscriptionBody
   */
  object UnsubscriptionBody {
    implicit val fmt = Json.format[UnsubscriptionBody]
  }

  /*
   * Represent subscription result
   */
  case class SubscriptionOpResult(r: R, fields: Set[FieldKey])

  //TODO change fields type
  /*
   * Represent implicit JSON Format for subscription operation result
   */
  object SubscriptionOpResult {
    implicit val fmt = Json.format[SubscriptionOpResult]
  }

  /*
   * Represent object data and meta data related to this object on read response
   */
  case class ReadItem(body: JsObject, meta: JsObject)

  /*
   * Represent implicit JSON Format for ReadItem
   */
  object ReadItem {
    implicit val fmt = Json.format[ReadItem]
  }

  /*
   * Represent tokens for read response
   */
  case class ReadTokens(nextToken: PaginationToken, prevToken: PaginationToken)

  /*
   * Represent implicit JSON Format for ReadTokens
   */
  object ReadTokens {
    implicit val fmt = Json.format[ReadTokens]
  }

  /*
   * Represent response body for read response
   */
  case class ReadResponseBody(tokens: Option[ReadTokens], totalResults: Option[Int], items: List[ReadItem])

  /*
   * Represent implicit JSON Format for Read Response Body
   */
  object ReadResponseBody {
    val read = Json.reads[ReadResponseBody]

    val write = (
      (__ \ "tokens").writeNullable[ReadTokens] and
      (__ \ "totalResults").writeNullable[Int] and
      (__ \ "items").write[List[ReadItem]]
    )(unlift(ReadResponseBody.unapply))

    implicit val fmt = Format[ReadResponseBody](read, write)
  }

  /*
   * Represent Snapshot for specific field
   */
  case class FieldSnapshot(key: String, value: JsValue)

  /*
   * Represent implicit JSON Format for FieldSnapshot
   */
  object FieldSnapshot {
    val reads = Json.reads[FieldSnapshot]

    object FieldSnapshotWrites extends Writes[FieldSnapshot] {
      def writes(f: FieldSnapshot): JsValue =
        Json.obj(f.key -> Json.obj("value" -> f.value))
    }

    implicit val fmt = Format[FieldSnapshot](reads, FieldSnapshotWrites)

    object FieldSnapshotListWrites extends Writes[List[FieldSnapshot]] {
      def writes(l: List[FieldSnapshot]): JsValue =
        Json.toJson(l.map(f => f.key -> Json.obj("value" -> f.value)).toMap)
    }

  }

  /*
   * Represent update operation that has processed on specific field
   */
  case class FieldUpdatedOp(key: FieldKey, op: UpdateCommand, opValue: Option[JsValue])

  /*
  * Represent implicit JSON Format for FieldSnapshot
  */
  object FieldUpdatedOp {
    val reads = Json.reads[FieldUpdatedOp]

    object FieldUpdatedOpWrites extends Writes[FieldUpdatedOp] {
      def writes(o: FieldUpdatedOp): JsValue =
        Json.obj(o.key -> JsObject(Seq(
          ("op" -> Json.toJson(o.op))
        ) ++
          o.opValue.map("opValue" -> _)))

    }

    implicit val fmt = Format[FieldUpdatedOp](reads, FieldUpdatedOpWrites)

    object FieldUpdatedOpListWrites extends Writes[List[FieldUpdatedOp]] {
      def writes(l: List[FieldUpdatedOp]): JsValue =
        Json.toJson(l.map(o => o.key -> JsObject(Seq(
          ("op" -> Json.toJson(o.op))
        ) ++
          o.opValue.map("opValue" -> _))).toMap)
    }

  }

  case class SubscriptionFailure(r: R, errorCode: Int, message: String)

  object SubscriptionFailure {

    object SubscriptionFailureWrites extends Writes[SubscriptionFailure] {
      def writes(sf: SubscriptionFailure): JsValue =
        Json.obj(
          "evt" -> "failed",
          "r" -> sf.r,
          "error" -> Json.obj(
            "code" -> sf.errorCode,
            "message" -> sf.message
          )
        )
    }

  }

}

