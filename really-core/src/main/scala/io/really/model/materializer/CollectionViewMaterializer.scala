/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.materializer

import akka.actor.{ FSM, Stash, ActorLogging }
import akka.persistence.{ SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer, PersistentView }
import io.really._
import _root_.io.really.model.materializer.CollectionViewMaterializer.{ MaterializerData, MaterializerState }
import _root_.io.really.model.{ ReferenceField, FieldKey, Model, Helpers }
import _root_.io.really.model.persistent.ModelRegistry.ModelOperation
import _root_.io.really.gorilla._
import _root_.io.really.model.CollectionActor.CollectionActorEvent
import _root_.io.really.model.materializer.MongoStorage._
import _root_.io.really.protocol.UpdateCommand
import _root_.io.really.protocol.UpdateOp
import play.api.libs.json.{ JsNumber, Json, JsObject }
import akka.pattern.pipe

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

class CollectionViewMaterializer(val globals: ReallyGlobals) extends PersistentView
    with FSM[MaterializerState, MaterializerData] with ActorLogging with Stash with MongoStorage {
  import CollectionViewMaterializer._

  /**
   * Bucket Id is used as identifier for a set of the objects in this collection
   */
  val bucketID: BucketID = self.path.name

  /**
   * r is used as identifier for R that represent this collection
   */
  val r: R = Helpers.getRFromBucketID(bucketID)

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
   * Collection Actor must explicitly update this view by sending [[Envelope]] request
   */
  override def autoUpdate: Boolean = false

  log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId starting with BucketID: $bucketID and R: $r")

  /**
   * materializerCurrentState is present current state for view and this used for debugging and testing
   */
  private var _materializerCurrentState: MaterializerDebuggingState = _

  /**
   * Return `materializerCurrentState`
   */
  def materializerCurrentState = _materializerCurrentState

  override def preStart() = {
    //create indexes
    defaultIndexes map (createIndex)
    _materializerCurrentState = MaterializerDebuggingState(None, None, lastSequenceNr, "without-model")
    super.preStart()
  }

  startWith(Initialization, Empty)

  when(Initialization)(handleRecover orElse handleModelCreated orElse stashMessage)
  when(WithModel)(handleModelOperations orElse handleCollectionEvents orElse handleInternalRequest orElse handleSnapshotResponse)
  when(WaitingDBOperation)(handleDBResponses orElse stashMessage)
  when(WithingReferenceField)(handleReferenceFieldOperations orElse stashMessage)

  initialize()

  /**
   * This function is responsible for handle messages when CollectionMaterializer restart and replay snapshot
   * @return
   */
  def handleRecover: StateFunction = {
    case Event(SnapshotOffer(metadata, snapshot: SnapshotData), _) =>
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      messageMarker = snapshot.marker
      _materializerCurrentState = _materializerCurrentState.copy(model = Some(snapshot.model), actorState = "with-model")
      unstashAll()
      goto(WithModel) using ModelData(snapshot.model, snapshot.referencedCollections)
  }

  /**
   * This function is responsible for handle [[ModelOperation.ModelCreated]] message
   */
  def handleModelCreated: StateFunction = {
    case Event(ModelOperation.ModelCreated(r, model, refCollections), _) =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId" +
        s" receive the model for r: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      // TODO create ReferenceUpdater Actor
      messageMarker = super.lastSequenceNr
      _materializerCurrentState = _materializerCurrentState.copy(
        model = Some(model), lastModelOp = Some("ModelCreated"), lastSequenceNr = lastSequenceNr, actorState = "with-model"
      )
      unstashAll()
      goto(WithModel) using ModelData(model, refCollections.toSet)
  }

  /**
   * This function is responsible for handle Model Operation messages like
   * [[ModelOperation.ModelUpdated]], [[ModelOperation.ModelDeleted]]
   * @return
   */
  def handleModelOperations: StateFunction = {
    case Event(ModelOperation.ModelUpdated(r, m, refCollections), _) if isPersistent =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId" +
        s" receive new version for model with r: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      messageMarker = super.lastSequenceNr
      _materializerCurrentState = _materializerCurrentState.copy(
        model = Some(m), lastModelOp = Some("ModelUpdated"), lastSequenceNr = lastSequenceNr
      )
      notifyGorillaEventCenter(ModelUpdatedEvent(bucketID, m))
      stay using ModelData(m, refCollections.toSet)

    case Event(ModelOperation.ModelDeleted(r), _) if isPersistent =>
      // TODO send clear message to Cleaner to delete all objects on this collection
      // TODO send Terminate to ReferenceUpdater
      notifyGorillaEventCenter(ModelDeletedEvent(bucketID))
      shutdown()
      stay
  }

  /**
   * This function responsible for handle Collection Events
   * @return
   */
  def handleCollectionEvents: StateFunction = {
    case Event(evt @ CollectionActorEvent.Created(r, obj, modelVersion, reqContext), ModelData(model, referencedCollections)) if isPersistent =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId " +
        s"receive create event for obj with R: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      val currentSequence = super.lastSequenceNr
      val referenceFields = getReferenceField(model, obj)
      if (referenceFields.isEmpty) {
        saveObject(obj, model) pipeTo self
        goto(WaitingDBOperation) using DBOperationInfo(
          DBOperation.Insert,
          None,
          model,
          referencedCollections,
          evt,
          currentSequence
        )
      } else {
        val expected = askAboutReferenceFields(referenceFields)
        goto(WithingReferenceField) using ReferenceFieldsData(
          DBOperation.Insert,
          obj,
          model,
          referencedCollections,
          Map.empty,
          expected,
          evt,
          currentSequence
        )
      }

    case Event(evt @ CollectionActorEvent.Updated(r, ops, rev, modelVersion, reqContext), ModelData(model, referencedCollections)) if isPersistent =>
      log.debug(s"CollectionViewMaterializer with viewId: $viewId for CollectionActor with persistentId: $persistenceId " +
        s"receive update event for obj with R: $r")
      log.debug(s"Current state for CollectionViewMaterializer with viewId: $viewId for CollectionActor with " +
        s"persistentId: $persistenceId: $materializerCurrentState")
      getObject(r) pipeTo self
      goto(WaitingDBOperation) using DBOperationInfo(DBOperation.Get, Some(DBOperation.Update), model, referencedCollections, evt, super.lastSequenceNr)

    case Event(evt @ CollectionActorEvent.Deleted(r, newRev, modelVersion, reqContext), ModelData(model, referencedCollections)) if isPersistent =>
      log.debug("CollectionViewMaterializer with viewId: {} for CollectionActor with persistentId:  {} " +
        "receive delete event for obj with R: {}", viewId, persistenceId, r)
      log.debug("Current state for CollectionViewMaterializer with viewId: {} for CollectionActor with " +
        "persistentId: {}: {}", viewId, persistenceId, materializerCurrentState)
      getObject(r) pipeTo self
      goto(WaitingDBOperation) using DBOperationInfo(DBOperation.Get, Some(DBOperation.Delete), model, referencedCollections, evt, super.lastSequenceNr)

  }

  /**
   * This function is responsible for handling requests between materializer view actors
   * @return
   */
  def handleInternalRequest: StateFunction = {
    case Event(GetObject(_, r), _) =>
      val requester = sender()
      getObject(r) map (requester ! _)
      stay
  }

  def handleSnapshotResponse: StateFunction = {
    case Event(SaveSnapshotSuccess(metadata), _) =>
      log.debug(s"Materializer received SaveSnapshotSuccess for snapshot: ${metadata}")
      stay()
    case Event(SaveSnapshotFailure(metadata, failure), _) =>
      log.error(failure, s"Materializer received SaveSnapshotFailure for snapshot: ${metadata}")
      stay()
  }

  def handleDBResponses: StateFunction = {
    case Event(OperationSucceeded(r, obj), DBOperationInfo(DBOperation.Insert, None, model, referencedCollections, event: CollectionActorEvent.Created, currentSequence)) =>
      persistAndNotifyGorilla(PersistentCreatedEvent(event), currentSequence, model, referencedCollections)
      unstashAll()
      goto(WithModel) using ModelData(model, referencedCollections)

    case Event(OperationSucceeded(r, obj), DBOperationInfo(DBOperation.Get, Some(DBOperation.Update), model, referencedCollections, event: CollectionActorEvent.Updated, currentSequence)) =>
      val (referencedOps, ops) = event.ops.partition(o => getModelReferenceField(model).contains(o.key))
      if (referencedOps.isEmpty) {
        val newObj = applyUpdateOps(obj, ops)
        updateObject(newObj, event.rev, event.modelVersion) pipeTo self
        stay using (DBOperationInfo(DBOperation.Update, None, model, referencedCollections, event, currentSequence))
      } else {
        val expected = askAboutReferenceFields(referencedOps.map(o => (o.key, o.value.as[R])).toMap)
        val newObj = applyUpdateOps(obj, ops)
        goto(WithingReferenceField) using ReferenceFieldsData(
          DBOperation.Update,
          newObj,
          model,
          referencedCollections,
          Map.empty,
          expected,
          event,
          currentSequence
        )
      }

    case Event(OperationSucceeded(r, obj), DBOperationInfo(DBOperation.Get, Some(DBOperation.Delete), model, referencedCollections, event: CollectionActorEvent.Deleted, currentSequence)) =>
      val _ / (_ / R.IdValue(id)) = r
      val newObj = obj.copy(obj.fields.filter(_._1.startsWith("_"))) ++ Json.obj(
        Model.DeletedField -> true
      )
      deleteObject(newObj, event.rev, event.modelVersion) pipeTo self
      stay using (DBOperationInfo(DBOperation.Delete, None, model, referencedCollections, event, currentSequence))

    case Event(OperationSucceeded(r, obj), DBOperationInfo(DBOperation.Update, None, model, referencedCollections, event: CollectionActorEvent.Updated, currentSequence)) =>
      persistAndNotifyGorilla(PersistentUpdatedEvent(event, obj), currentSequence, model, referencedCollections)
      //TODO notify to update any object refer to this object
      unstashAll()
      goto(WithModel) using ModelData(model, referencedCollections)

    case Event(OperationSucceeded(r, obj), DBOperationInfo(DBOperation.Delete, None, model, referencedCollections, event: CollectionActorEvent.Deleted, currentSequence)) =>
      persistAndNotifyGorilla(PersistentDeletedEvent(event), currentSequence, model, referencedCollections)
      //TODO notify to update any object refer to this object
      unstashAll()
      goto(WithModel) using ModelData(model, referencedCollections)

    case Event(OperationFailed(_, failure), DBOperationInfo(_, _, model, referencedCollections, _, _)) =>
      context.parent ! failure
      shutdown()
      unstashAll()
      goto(WithModel) using ModelData(model, referencedCollections)
  }

  def handleReferenceFieldOperations: StateFunction = {
    case e @ Event(OperationSucceeded(r, obj), data @ ReferenceFieldsData(_, _, _, _, _, expected, _, _)) if !expected.contains(r) =>
      log.warning("Unexpected Reference. Probably Coding bug. Event was: {}", e)
      stay

    case Event(OperationSucceeded(r, obj), data @ ReferenceFieldsData(DBOperation.Insert, _, _, _, received, expected, _, _)) =>
      val referencesObjects = received + (expected(r) -> obj)
      val newExpected = expected - r
      if (newExpected.isEmpty) {
        val newObj = writeReferenceField(data.obj, referencesObjects, data.model)
        saveObject(newObj, data.model) pipeTo self
        unstashAll()
        goto(WaitingDBOperation) using DBOperationInfo(
          data.operation,
          None,
          data.model,
          data.referencedCollections,
          data.collectionEvent,
          data.currentMessageNum
        )
      } else {
        stay using data.copy(received = referencesObjects, expected = newExpected)
      }

    case Event(OperationSucceeded(r, obj), data @ ReferenceFieldsData(DBOperation.Update, _, _, _, received, expected, event: CollectionActorEvent.Updated, _)) =>
      val referencesObjects = received + (expected(r) -> obj)
      val newExpected = expected - r
      if (newExpected.isEmpty) {
        val newObj = writeReferenceField(data.obj, referencesObjects, data.model)
        updateObject(newObj, event.rev, event.modelVersion) pipeTo self
        unstashAll()
        goto(WaitingDBOperation) using DBOperationInfo(
          data.operation,
          None,
          data.model,
          data.referencedCollections,
          data.collectionEvent,
          data.currentMessageNum
        )
      } else {
        stay using data.copy(received = referencesObjects, expected = newExpected)
      }
  }

  /**
   * apply update operations on object
   */
  private def applyUpdateOps(obj: JsObject, ops: List[UpdateOp]): JsObject =
    ops match {
      case Nil => obj
      case ops =>
        val result: List[JsObject] = ops map {
          case UpdateOp(UpdateCommand.Set, key, value, _) =>
            obj ++ Json.obj(key -> value)
          case UpdateOp(UpdateCommand.AddNumber, key, JsNumber(v), _) =>
            obj ++ Json.obj(key -> ((obj \ key).as[JsNumber].value + v))
        }
        result.foldLeft(Json.obj())((o, a) => a ++ o)
    }

  /**
   * notify gorillaEventCenter
   */
  def notifyGorillaEventCenter(event: ModelEvent) = globals.gorillaEventCenter ! event

  /**
   * This function is responsible for stashing message if it should handle on another state
   * @return
   */
  def stashMessage: StateFunction = {
    case msg =>
      stash()
      stay()
  }

  /**
   * send event to gorilla event center and create snapshot
   * @param evt
   * @param currentSequence
   * @param model
   * @param referencedCollections
   */
  private def persistAndNotifyGorilla(evt: PersistentEvent, currentSequence: Long, model: Model, referencedCollections: Set[R]) = {
    globals.gorillaEventCenter ! evt
    messageMarker = currentSequence
    _materializerCurrentState = _materializerCurrentState.copy(lastSequenceNr = lastSequenceNr)
    takeSnapshot(model, referencedCollections, currentSequence)
  }

  /**
   * save snapshot for this view
   */
  private def takeSnapshot(model: Model, referencedCollections: Set[R], marker: Long): Unit = {
    messageMarker = marker
    saveSnapshot(SnapshotData(model, referencedCollections, marker))
  }

  /**
   * shutdown Marterializer
   */
  private def shutdown(): Unit = {
    context.stop(self)
  }

  /**
   * This function is responsible for get reference fields from object based on model schema
   * @param model
   * @param obj
   * @return
   */
  def getReferenceField(model: Model, obj: JsObject): Map[FieldKey, R] =
    model.fields collect {
      case (key, ReferenceField(_, true, _, _)) =>
        key -> (obj \ key).as[R]
      case (key, ReferenceField(_, false, _, _)) if ((obj \ key).asOpt[R]).isDefined =>
        key -> (obj \ key).as[R]
    }

  def getModelReferenceField(model: Model): Map[FieldKey, ReferenceField] =
    model.fields collect {
      case f @ (key, rf: ReferenceField) => key -> rf
    }

  /**
   * This function is responsible for send messages to get object for all reference field
   * @param fields
   */
  def askAboutReferenceFields(fields: Map[FieldKey, R]): Map[R, FieldKey] =
    fields.map { f =>
      globals.materializerView ! GetObject(Helpers.getBucketIDFromR(f._2)(globals.config), f._2)
      f._2 -> f._1
    }

  def writeReferenceField(obj: JsObject, referencedFields: Map[FieldKey, JsObject], model: Model): JsObject = {
    val dereferenceFields = referencedFields map {
      field =>
        val fields = (model.fields(field._1).asInstanceOf[ReferenceField].fields ++ List(Model.RField, Model.RevisionField)).toSet
        val obj = JsObject(field._2.value.filter(f => fields.contains(f._1)).toSeq)
        val _r = (field._2 \ "_r").as[R]
        field._1 -> Json.obj("value" -> _r, "ref" -> obj)
    }
    obj deepMerge JsObject(dereferenceFields.toSeq)
  }

}

object CollectionViewMaterializer {

  sealed trait MaterializerState
  case object Initialization extends MaterializerState
  case object WithModel extends MaterializerState
  case object WaitingDBOperation extends MaterializerState
  case object WithingReferenceField extends MaterializerState

  sealed trait MaterializerData
  case object Empty extends MaterializerData
  case class ModelData(model: Model, referencedCollections: Set[R]) extends MaterializerData
  case class DBOperationInfo(
    operation: DBOperation,
    nextOperation: Option[DBOperation],
    model: Model,
    referencedCollections: Set[R],
    collectionEvent: CollectionActorEvent,
    currentMessageNum: Long
  ) extends MaterializerData
  case class ReferenceFieldsData(
    operation: DBOperation,
    obj: JsObject,
    model: Model,
    referencedCollections: Set[R],
    received: Map[FieldKey, JsObject],
    expected: Map[R, FieldKey],
    collectionEvent: CollectionActorEvent,
    currentMessageNum: Long
  ) extends MaterializerData

  trait RoutableToMaterializer {
    def bucketId: BucketID
  }

  case class GetObject(bucketId: BucketID, r: R) extends RoutableToMaterializer

  case class Envelope(bucketId: BucketID, message: Any) extends RoutableToMaterializer

  case class SnapshotData(model: Model, referencedCollections: Set[R], marker: Long)

  case class MaterializerDebuggingState(model: Option[Model], lastModelOp: Option[String], lastSequenceNr: Long, actorState: String)

}
