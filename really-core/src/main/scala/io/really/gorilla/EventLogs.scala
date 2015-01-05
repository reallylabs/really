/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import io.really.model.ModelVersion
import io.really.protocol.UpdateOp
import io.really.{ R, UserInfo, Revision }
import play.api.libs.json.{ JsObject, Json }

import scala.slick.driver.H2Driver.simple._

case class EventLog(event: String, r: R, rev: Revision, modelVersion: ModelVersion,
  obj: JsObject, userInfo: UserInfo, ops: Option[List[UpdateOp]])

class EventLogs(tag: Tag)
    extends Table[EventLog](tag, EventLogs.tableName) with EventLogMappers {

  implicit val rType = rColumnType

  implicit val userType = userInfoColumnType

  implicit val objType = objColumnType

  implicit val opsType = opsColumnType

  def eventType: Column[String] = column[String]("TYPE")
  def r: Column[R] = column[R]("R")
  def rev: Column[Long] = column[Long]("REV")
  def modelVersion: Column[Long] = column[Long]("MODEL_REVISION")
  def obj: Column[JsObject] = column[JsObject]("OBJ")
  def user: Column[UserInfo] = column[UserInfo]("USER")
  def ops: Column[Option[List[UpdateOp]]] = column[Option[List[UpdateOp]]]("OPS")
  def * = (eventType, r, rev, modelVersion, obj, user, ops) <> (EventLog.tupled, EventLog.unapply)
}

trait EventLogMappers {
  // And a ColumnType that maps R it to String
  val rColumnType = MappedColumnType.base[R, String](
    { rObj => rObj.toString }, // map R to String
    { rString => R(rString)
    } // map String to R
  )

  // And a ColumnType that maps UserInfo it to String
  val userInfoColumnType = MappedColumnType.base[UserInfo, String](
    { infoObj => Json.stringify(UserInfo.fmt.writes(infoObj)) }, // map UserInfo to String
    { infoString =>
      UserInfo.fmt.reads(Json.parse(infoString)).get
    } // map String to UserInfo
  )

  // And a ColumnType that maps JsObject it to String
  val objColumnType = MappedColumnType.base[JsObject, String](
    { jsObj => Json.stringify(jsObj) }, // map JsObject to String
    { objString => Json.parse(objString).asInstanceOf[JsObject] } // map String to JsObject
  )

  // And a ColumnType that maps List[UpdateOp] it to String
  val opsColumnType = MappedColumnType.base[List[UpdateOp], String](
    { ops => Json.stringify(Json.toJson(ops)) }, // map List[UpdateOp] to String
    { opsString => Json.fromJson[List[UpdateOp]](Json.parse(opsString)).get
    } // map String to List[UpdateOp]
  )

}
object EventLogs extends EventLogMappers {
  val tableName = "EVENTS"

  implicit val rType = rColumnType

  implicit val userType = userInfoColumnType

  implicit val objType = objColumnType

  implicit val opsType = opsColumnType

}

object EventLogMarkers extends EventLogMappers {
  val tableName = "EVENTS-MARKERS"
}

class EventLogMarkers(tag: Tag)
    extends Table[(R, Long)](tag, EventLogMarkers.tableName) with EventLogMappers {

  implicit val rType = rColumnType

  def r: Column[R] = column[R]("R", O.PrimaryKey)
  def rev: Column[Long] = column[Long]("REV")
  def * = (r, rev)
}
