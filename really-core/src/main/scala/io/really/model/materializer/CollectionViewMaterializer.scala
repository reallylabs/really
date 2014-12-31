/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.materializer

import akka.actor.{ Stash, ActorLogging }
import akka.persistence.{ SnapshotOffer, PersistentView, Update }
import io.really.json.collection.JSONCollection
import io.really.model.persistent.ModelRegistry._
import io.really.model.CollectionActor._
import io.really.gorilla.{
  ModelUpdatedEvent,
  PersistentCreatedEvent,
  PersistentUpdatedEvent,
  PersistentEvent,
  ModelDeletedEvent,
  PersistentDeletedEvent
}
import io.really.protocol.{ UpdateCommand, UpdateOp }
import io.really.model._
import io.really._
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.core.errors.DatabaseException
import scala.concurrent.Future
import play.api.libs.json._
import org.joda.time._

/**
 * Collection view Materializer is Akka Persistent view for Collection Actor Persistent
 * that receives a message when a collection bucket is updated so that it can replay
 * the ordered events and generate a set of projections that is optimized for queries by the user.
 *
 * The Materializer is a PersistentView that replays the journal log and restores
 * the last snapshot, constructing a projection database that is purely optimized
 * for read performance and send the event to Gorilla Event Centre.
 *
 * @param globals
 */
class CollectionViewMaterializer(globals: ReallyGlobals) extends PersistentView with ActorLogging with Stash {
  import CollectionViewMaterializer._
  import MaterializerFailure._

  /**
   * Bucket Id is used as identifier for a set of the objects in this collection
   */
  val bucketID: BucketID = self.path.name

  /**
   * r is used as identifier for R that represent this collection
   */
  val r: R = Helpers.getRFromBucketID(bucketID)

  /**
   * collectionName is used as identifier for collection name on DB
   */
  val collectionName = r.collectionName

  /**
   * collection is represent collection object on MongoDB
   */
  lazy val collection = globals.mongodbConnection.collection[JSONCollection](s"$collectionName")

  /**
   * messageMarker is marker for last message consumed and persisted on DB Projection
   */
  private var messageMarker: Long = 0L

  implicit val ec = context.dispatcher

  override def viewId: String = s"view_materializer${bucketID}"

  override def persistenceId: String = bucketID

  /**
   * Returns `lastSequenceNr`.
   */
  override def lastSequenceNr: Long = messageMarker

  /**
   * The Materializer does not fetch events from the journal automatically,
   * Collection Actor must explicitly update this view by sending [[UpdateProjection]] request
   */
  override def autoUpdate: Boolean = false

  log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId starting with BucketID: $bucketID and R: $r")

  private val defaultIndexes: Seq[Index] = r.tail.foldLeft(Seq(Index(Seq("_r" -> IndexType.Ascending), unique = true)))(
    (indexes, token) => indexes.+:(Index(Seq((s"_parent${r.tail.indexOf(token)}" -> IndexType.Ascending)), unique = false))
  )

  /**
   * materializerCurrentState is present current state for view and this used for debugging and testing
   */
  private var _materializerCurrentState: MaterializerState = _

  /**
   * Return `materializerCurrentState`
   */
  def materializerCurrentState = _materializerCurrentState

  override def preStart() = {
    //create indexes
    defaultIndexes map (createIndex)
    _materializerCurrentState = MaterializerState(None, None, lastSequenceNr, "without-model")
    super.preStart()
  }

  override def receive: Receive = withoutModel

  def withoutModel: Receive = {
    case SnapshotOffer(metadata, snapshot: SnapshotData) =>
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      messageMarker = snapshot.marker
      _materializerCurrentState = _materializerCurrentState.copy(model = Some(snapshot.model), actorState = "with-model")
      context.become(withModel(snapshot.model, snapshot.referencedCollections))

    case ModelOperation.ModelCreated(r, model, refCollections) =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId" +
        s" receive the model for r: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      // TODO create ReferenceUpdater Actor
      messageMarker = super.lastSequenceNr
      _materializerCurrentState = _materializerCurrentState.copy(
        model = Some(model), lastModelOp = Some("ModelCreated"), lastSequenceNr = lastSequenceNr, actorState = "with-model"
      )
      context.become(withModel(model, refCollections))
      unstashAll()

    case msg =>
      stash()
  }

  def withModel(model: Model, referencedCollections: List[R]): Receive = {
    case evt @ CollectionActorEvent.Created(r, obj, modelVersion, reqContext) if isPersistent =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId " +
        s"receive create event for obj with R: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      val currentSequence = super.lastSequenceNr
      saveObject(obj, model) map {
        case Right(_) =>
          persistEvent(PersistentCreatedEvent(evt), currentSequence, model, referencedCollections)
        case Left(failure) =>
          context.parent ! failure
          shutdown()
      }

    case evt @ CollectionActorEvent.Updated(r, ops, rev, modelVersion, reqContext) if isPersistent =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId " +
        s"receive update event for obj with R: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      val currentSequence = super.lastSequenceNr
      getObject(r) map {
        case Right(obj) =>
          val newObj = applyUpdateOps(obj, ops)
          updateObject(newObj, rev, modelVersion) map {
            case Right(_) =>
              persistEvent(PersistentUpdatedEvent(evt, newObj), currentSequence, model, referencedCollections)
            case Left(failure) =>
              context.parent ! failure
              shutdown()
          }
        case Left(failure) =>
          context.parent ! failure
          shutdown()
      }

    case evt @ CollectionActorEvent.Deleted(r, newRev, modelVersion, reqContext) if isPersistent =>
      log.debug("CollectionViewMaterializer with viewId: {} for CollectionActor with persistentId: {} " +
        "receive delete event for obj with R: {}", viewId, persistenceId, r)
      log.debug("Current state for CollectionViewMaterializer with viewId: {} for CollectionActor with " +
        "persistentId: {}: {}", viewId, persistenceId, materializerCurrentState)
      val currentSequence = super.lastSequenceNr
      getObject(r) map {
        case Right(obj) =>
          val _ / (_ / R.IdValue(id)) = r
          val newObj = obj.copy(obj.fields.filter(_._1.startsWith("_"))) ++ Json.obj(
            Model.DeletedField -> true
          )
          deleteObject(newObj, newRev, modelVersion) map {
            case Right(_) =>
              persistEvent(PersistentDeletedEvent(evt), currentSequence, model, referencedCollections)
            case Left(failure) =>
              context.parent ! failure
              shutdown()
          }
        case Left(failure) =>
          context.parent ! failure
          shutdown()
      }

    case ModelOperation.ModelUpdated(r, m, refCollections) if isPersistent =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId" +
        s" receive new version for model with r: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      messageMarker = super.lastSequenceNr
      _materializerCurrentState = _materializerCurrentState.copy(
        model = Some(m), lastModelOp = Some("ModelUpdated"), lastSequenceNr = lastSequenceNr
      )
      globals.gorillaEventCenter ! ModelUpdatedEvent(bucketID, m)
      context.become(withModel(m, refCollections))

    case ModelOperation.ModelDeleted(r) if isPersistent =>
      // TODO send clear message to Cleaner to delete all objects on this collection
      // TODO send Terminate to ReferenceUpdater
      globals.gorillaEventCenter ! ModelDeletedEvent(bucketID)
      shutdown()
  }

  /**
   * save snapshot for this view
   */
  private def takeSnapshot(model: Model, referencedCollections: List[R], marker: Long): Unit = {
    messageMarker = marker
    saveSnapshot(SnapshotData(model, referencedCollections, marker))
  }

  /**
   * get object with specific r from Projection DB
   * @param r is represent r for object
   * @return Future[ObjectResult]
   */
  private def getObject(r: R, collection: JSONCollection = collection): Future[Either[MaterializerFailure, JsObject]] = {
    val query = Json.obj("_r" -> r)
    val cursor: Cursor[JsObject] = collection.find(query).cursor[JsObject]
    cursor.headOption map {
      case Some(obj) =>
        Right(obj)
      case None =>
        log.debug(s"Collection Materializer try to get object with R: $r, and this object was not found on DB.")
        Left(ObjectNotFound(r))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during getting $r on collection Materializer, error: ", e)
        Left(OperationNotComplete("get", e))
      case e: Throwable =>
        log.error(s"Unexpected error happened during getting $r on collection Materializer, error: ", e)
        Left(OperationNotComplete("get", e))
    }
  }

  /**
   * Save new object on DB
   * @param obj is present obj data
   */
  private def saveObject(obj: JsObject, model: Model): Future[Either[MaterializerFailure, Unit]] = {
    // TODO Dereference reference fields
    collection.insert(addMetaData(obj, 1L, model.collectionMeta.version, Some(DateTime.now()))) map {
      case lastError if lastError.ok => Right(())
      case lastError =>
        log.error(s"Failure while Materializer trying to insert obj: $obj on DB, error: ${lastError.message}")
        Left(OperationNotComplete("update", new Exception(lastError.message)))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during insert obj: $obj on collection Materializer, error: ", e)
        Left(OperationNotComplete("insert", e))
      case e: Throwable =>
        log.error(s"Unexpected error happened during insert obj: $obj on collection Materializer, error: ", e)
        Left(OperationNotComplete("insert", e))
    }
  }

  /**
   * Update object on DB
   */
  private def updateObject(obj: JsObject, objRevision: Revision, modelVersion: ModelVersion): Future[Either[MaterializerFailure, Unit]] = {
    collection.save(addMetaData(obj, objRevision, modelVersion)) map {
      case lastError if lastError.ok => Right(())
      case lastError =>
        log.error(s"Failure while Materializer trying to update obj: $obj on DB, error: ${lastError.message}")
        Left(OperationNotComplete("update", new Exception(lastError.message)))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during save obj: $obj on collection Materializer, error: ", e)
        Left(OperationNotComplete("update", e))
      case e: Throwable =>
        log.error(s"Unexpected error happened during save obj: $obj on collection Materializer, error: ", e)
        Left(OperationNotComplete("update", e))
    }
  }

  /**
   * apply update operations on object
   */
  private def applyUpdateOps(obj: JsObject, ops: List[UpdateOp]): JsObject = {
    val result: List[JsObject] = ops map {
      case UpdateOp(UpdateCommand.Set, key, value, _) =>
        obj ++ Json.obj(key -> value)
      case UpdateOp(UpdateCommand.AddNumber, key, JsNumber(v), _) =>
        obj ++ Json.obj(key -> ((obj \ key).as[JsNumber].value + v))
    }
    result.foldLeft(Json.obj())((o, a) => a ++ o)
  }

  /**
   * Delete object from DB
   */
  private def deleteObject(obj: JsObject, objRevision: Revision, modelVersion: ModelVersion): Future[Either[MaterializerFailure, Unit]] = {
    collection.save(addMetaData(obj, objRevision, modelVersion)) map {
      case lastError if lastError.ok =>
        Right(())
      case lastError =>
        log.error(s"Failure while Materializer trying to delete obj: $obj on DB, error: ${lastError.message}")
        Left(OperationNotComplete("delete", new Exception(lastError.message)))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during save obj: $obj on collection Materializer, error: ", e)
        Left(OperationNotComplete("delete", e))
      case e: Throwable =>
        log.error(s"Unexpected error happened during save obj: $obj on collection Materializer, error: ", e)
        Left(OperationNotComplete("delete", e))
    }
  }

  /**
   * shutdown Marterializer
   */
  private def shutdown(): Unit = {
    context.stop(self)
  }

  /**
   * add meta data and DB fields for new object
   */
  private def addMetaData(obj: JsObject, objRevision: Revision, modelVersion: ModelVersion, createdAt: Option[DateTime] = None): JsObject = {
    val r = (obj \ "_r").as[R]
    val parentsData = r.tail.foldLeft(Json.obj())(
      (data, token) => data ++ Json.obj(s"_parent${r.tail.indexOf(token)}" -> R / token)
    )
    val createdTime = createdAt match {
      case Some(time) => time
      case None => (obj \ "_metaData" \ "createdAt").as[DateTime]
    }
    obj ++ parentsData ++ Json.obj(
      "_id" -> r.head.id.get,
      "_rev" -> objRevision,
      "_metaData" -> Json.obj(
        "modelVersion" -> modelVersion,
        "updatedAt" -> DateTime.now(),
        "createdAt" -> createdTime
      )
    )
  }

  /**
   * create Mongo Index
   * @param index
   */
  private def createIndex(index: Index): Future[Unit] =
    collection.indexesManager.ensure(index) map { created =>
      if (created)
        log.debug(s"mongo index with $index key created.")
      else
        log.debug(s"mongo index with $index key already created.")
    }

  /**
   * send event to gorilla event center and create snapshot
   * @param evt
   * @param currentSequence
   * @param model
   * @param referencedCollections
   */
  private def persistEvent(evt: PersistentEvent, currentSequence: Long, model: Model, referencedCollections: List[R]) = {
    globals.gorillaEventCenter ! evt
    messageMarker = currentSequence
    _materializerCurrentState = _materializerCurrentState.copy(lastSequenceNr = lastSequenceNr)
    takeSnapshot(model, referencedCollections, currentSequence)
  }

}

object CollectionViewMaterializer {

  trait RoutableToMaterializer {
    def bucketId: BucketID
  }

  case class Envelope(bucketId: BucketID, message: Any) extends RoutableToMaterializer

  case class SnapshotData(model: Model, referencedCollections: List[R], marker: Long)

  /**
   * Materializer Failures
   */
  trait MaterializerFailure

  object MaterializerFailure {

    case class EventSavedFailed(failure: Throwable) extends MaterializerFailure

    case class ObjectNotFound(r: R) extends MaterializerFailure

    case class OperationNotComplete(op: String, error: Throwable) extends MaterializerFailure

  }

  case class MaterializerState(model: Option[Model], lastModelOp: Option[String], lastSequenceNr: Long, actorState: String)

}
