/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import io.really.model.ModelVersion
import io.really.{ Revision }

import scala.slick.driver.H2Driver.simple._

case class EventLog(event: String, r: String, rev: Revision, modelVersion: ModelVersion,
  obj: String, userInfo: String, ops: Option[String])

class EventLogs(tag: Tag)
    extends Table[EventLog](tag, EventLogs.tableName) {

  def eventType: Column[String] = column[String]("TYPE")
  def r: Column[String] = column[String]("R")
  def rev: Column[Long] = column[Long]("REV")
  def ModelVersion: Column[Long] = column[Long]("MODEL_REVISION")
  def obj: Column[String] = column[String]("OBJ")
  def user: Column[String] = column[String]("USER")
  def ops: Column[Option[String]] = column[Option[String]]("OPS")
  def * = (eventType, r, rev, ModelVersion, obj, user, ops) <> (EventLog.tupled, EventLog.unapply)
}

object EventLogs {
  val tableName = "EVENTS"
}