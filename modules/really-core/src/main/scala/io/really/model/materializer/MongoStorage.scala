/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.materializer

import io.really._
import _root_.io.really.json.collection.JSONCollection
import _root_.io.really.model.{ ModelVersion, Model }
import org.joda.time.DateTime
import play.api.libs.json.{ Json, JsObject }
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{ IndexType, Index }
import reactivemongo.core.errors.DatabaseException

import scala.concurrent.Future
import scala.util.control.NonFatal

trait MongoStorage {
  self: CollectionViewMaterializer =>

  import MongoStorage._
  import DBOperation._

  /**
   * collectionName is used as identifier for collection name on DB
   */
  lazy val collectionName = r.collectionName

  /**
   * collection is represent collection object on MongoDB
   */
  lazy val collection = globals.mongodbConnection.collection[JSONCollection](s"$collectionName")

  lazy val defaultIndexes: Seq[Index] = r.tail.foldLeft(Seq(Index(Seq("_r" -> IndexType.Ascending), unique = true)))(
    (indexes, token) => indexes.+:(Index(Seq((s"_parent${r.tail.indexOf(token)}" -> IndexType.Ascending)), unique = false))
  )

  /**
   * create Mongo Index
   * @param index
   */
  def createIndex(index: Index): Future[Unit] =
    collection.indexesManager.ensure(index) map { created =>
      if (created)
        log.debug(s"mongo index with $index key created.")
      else
        log.debug(s"mongo index with $index key already created.")
    }

  /**
   * get object with specific r from Projection DB
   * @param r is represent r for object
   * @return Future[ObjectResult]
   */
  def getObject(r: R, collection: JSONCollection = collection): Future[DBResponse] = {
    val query = Json.obj("_r" -> r)
    val cursor: Cursor[JsObject] = collection.find(query).cursor[JsObject]
    cursor.headOption map {
      case Some(obj) =>
        OperationSucceeded(r, obj)
      case None =>
        log.debug(s"Collection Materializer try to get object with R: $r, and this object was not found on DB.")
        ObjectNotFound(r)
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during getting $r on collection Materializer, error: $e")
        OperationFailed(Get, e)
      case NonFatal(e) =>
        log.error(s"Unexpected error happened during getting $r on collection Materializer, error: $e")
        OperationFailed(Get, e)
    }
  }

  /**
   * Save new object on DB
   * @param obj is present obj data
   */
  def saveObject(obj: JsObject, model: Model): Future[DBResponse] = {
    // TODO Dereference reference fields
    val completeObj = addMetaData(obj, 1L, model.collectionMeta.version, Some(DateTime.now()))
    val r = (completeObj \ "_r").as[R]
    collection.insert(completeObj) map {
      case lastError if lastError.ok =>
        OperationSucceeded(r, completeObj)
      case lastError =>
        log.error(s"Failure while Materializer trying to insert obj: $obj on DB, error: ${lastError.message}")
        OperationFailed(Insert, new Exception(lastError.message))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during insert obj: $obj on collection Materializer, error: $e")
        OperationFailed(Insert, e)
      case e: Throwable =>
        log.error(s"Unexpected error happened during insert obj: $obj on collection Materializer, error: $e")
        OperationFailed(Insert, e)
    }
  }

  /**
   * Update object on DB
   */
  def updateObject(obj: JsObject, objRevision: Revision, modelVersion: ModelVersion): Future[DBResponse] = {
    val newObj = addMetaData(obj, objRevision, modelVersion)
    val r = (obj \ "_r").as[R]
    collection.save(newObj) map {
      case lastError if lastError.ok =>
        OperationSucceeded(r, newObj)
      case lastError =>
        log.error(s"Failure while Materializer trying to update obj: $obj on DB, error: ${lastError.message}")
        OperationFailed(Update, new Exception(lastError.message))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during save obj: $obj on collection Materializer, error: $e")
        OperationFailed(Update, e)
      case NonFatal(e) =>
        log.error(s"Unexpected error happened during save obj: $obj on collection Materializer, error: $e")
        OperationFailed(Update, e)
    }
  }

  /**
   * Delete object from DB
   */
  def deleteObject(obj: JsObject, objRevision: Revision, modelVersion: ModelVersion): Future[DBResponse] = {
    val deletedObj = addMetaData(obj, objRevision, modelVersion)
    val r = (obj \ "_r").as[R]
    collection.save(deletedObj) map {
      case lastError if lastError.ok =>
        OperationSucceeded(r, deletedObj)
      case lastError =>
        log.error(s"Failure while Materializer trying to delete obj: $obj on DB, error: ${lastError.message}")
        OperationFailed(Delete, new Exception(lastError.message))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during save obj: $obj on collection Materializer, error: $e")
        OperationFailed(Delete, e)
      case NonFatal(e) =>
        log.error(s"Unexpected error happened during save obj: $obj on collection Materializer, error: $e")
        OperationFailed(Delete, e)
    }
  }

  /**
   * add meta data and DB fields for new object
   */
  private def addMetaData(obj: JsObject, objRevision: Revision, modelVersion: ModelVersion, createdAt: Option[DateTime] = None): JsObject = {
    val r = (obj \ "_r").as[R]
    val parentsData = r.tail.foldLeft(Json.obj())(
      (data, token) => data ++ Json.obj(s"_parent${r.tail.indexOf(token) + 1}" -> R / token)
    )
    val createdTime = createdAt match {
      case Some(time) => time
      case None => (obj \ "_metaData" \ "createdAt").as[DateTime]
    }
    obj ++ parentsData ++ Json.obj(
      "_id" -> Json.toJson(r.head.id.get.toString),
      "_rev" -> objRevision,
      "_metaData" -> Json.obj(
        "modelVersion" -> modelVersion,
        "updatedAt" -> DateTime.now(),
        "createdAt" -> createdTime
      )
    )
  }

}

object MongoStorage {

  sealed trait DBResponse

  case class ObjectNotFound(r: R) extends DBResponse
  case class OperationFailed(operation: DBOperation, error: Throwable) extends DBResponse
  case class OperationSucceeded(r: R, obj: JsObject) extends DBResponse

  sealed trait DBOperation
  object DBOperation {

    case object Insert extends DBOperation

    case object Update extends DBOperation

    case object Delete extends DBOperation

    case object Get extends DBOperation

  }
}
