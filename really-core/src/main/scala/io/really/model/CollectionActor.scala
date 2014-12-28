/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor._
import akka.contrib.pattern.ShardRegion.Passivate
import akka.persistence.{ RecoveryFailure, RecoveryCompleted, SnapshotOffer, PersistentActor }
import io.really.CommandError._
import io.really.Request.{ Update, Create }
import io.really.Result.{ UpdateResult, CreateResult }
import io.really._
import _root_.io.really.protocol.{ UpdateCommand, UpdateOp }
import _root_.io.really.js.JsResultHelpers
import _root_.io.really.model.persistent.ModelRegistry
import ModelRegistry.RequestModel.GetModel
import _root_.io.really.model.persistent.ModelRegistry.ModelOperation.{ ModelCreated, ModelUpdated, ModelDeleted }
import ModelRegistry.ModelResult
import _root_.io.really.model.materializer.CollectionViewMaterializer
import _root_.play.api.data.validation.ValidationError
import play.api.libs.json._

/**
 * Collection Actor is responsible for:
 * Handling group of objects falls in a specific bucket for a specific collection
 * Handling Create command
 * Handling Update Command
 * Handling Delete Command
 */
class CollectionActor(globals: ReallyGlobals) extends PersistentActor
    with FSM[CollectionActor.CollectionState, CollectionActor.CollectionData] with ActorLogging with Stash {

  import CollectionActor._

  protected var t1 = 0l

  context.setReceiveTimeout(globals.config.CollectionActorConfig.idleTimeout)

  /**
   * state variable represent objects on specific bucket
   */
  protected var bucket: Buckets = Map.empty

  val bucketId: BucketID = self.path.name

  override val persistenceId = bucketId

  val r: R = Helpers.getRFromBucketID(bucketId)

  log.debug(s"$persistenceId Persistor started with BucketID: $bucketId and R: $r")

  startWith(Initialization, Empty)

  override def preStart() = {
    t1 = System.currentTimeMillis()
    super.preStart()
  }

  override def receiveRecover: Receive = {
    case evt: CollectionActorEvent.Created =>
      log.debug(s"$persistenceId Persistor received a Create replay event: $evt")
      updateBucket(evt)

    case evt @ CollectionActorEvent.Updated(r, ops, newRev, modelVersion, ctx) if bucket.get(r).isDefined =>
      log.debug(s"$persistenceId Persistor received an Update replay event: $evt")
      val modelObj = bucket(r)
      getUpdatedData(modelObj, ops, rev(modelObj.data)) match {
        case JsSuccess(obj, _) => updateBucket(evt, Some(obj))
        case e: JsError =>
          log.error(s"An error occurred while constructing the updating object with this error $e")
        //TODO: mark the object to be corrupted in the future
      }

    case evt: CollectionActorEvent =>
      log.warning(s"[[NOT IMPLEMENTED]] Got EVENT: $evt")
    //TODO: handle other events

    //TODO handle saveSnapshot()
    case SnapshotOffer(_, snapshot: Map[R @unchecked, DataObject @unchecked]) => bucket = snapshot

    case RecoveryCompleted =>
      log.debug(s"$persistenceId Persistor took to recover was {}ms", System.currentTimeMillis - t1)
      globals.modelRegistry ! GetModel(r, self)

    case RecoveryFailure(cause) =>
      log.error(s"$persistenceId Persistor failed to recover this cause: $cause")
      context.parent ! Passivate(stopMessage = Stop)
  }

  override val receiveCommand: Receive = {
    case msg =>
      log.debug(s"Collection Actor for bucketId: ${bucketId} received message: ${msg}")
  }

  when(Initialization)(handleModelRegistryReply orElse stashMessage)

  when(WithoutModel)(handleGeneralAkkaMessages orElse replyWithModelNotFound orElse replyWithInvalidCommand)

  when(WaitingParentValidation)(handleParentValidationResult orElse stashMessage)

  when(WaitingReferencesValidation)(handleReferencesValidationResults orElse stashMessage)

  when(WithModel)(handleModelOperations orElse
    handleInternalRequest orElse
    handleRequests orElse
    handleGeneralAkkaMessages orElse
    replyWithInvalidCommand)

  initialize()

  /**
   * This function is responsible for handling messages that received from [[persistent.ModelRegistry]]
   * @return
   */
  def handleModelRegistryReply: StateFunction = {
    case Event(ModelResult.ModelNotFound, _) =>
      log.debug(s"$persistenceId Persistor couldn't find the model for r: $r")
      context.parent ! Passivate(stopMessage = Stop)
      unstashAll()
      goto(WithoutModel)

    case Event(ModelResult.ModelObject(m, refL), _) =>
      log.debug(s"$persistenceId Persistor found the model for r: $r")
      persist(ModelCreated(m.r, m, refL)) {
        event =>
          askMaterializerToUpdate
      }
      unstashAll()
      goto(WithModel) using ModelData(m)
  }

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
   * This function is responsible for handling General Actor Messages like [[ReceiveTimeout]], [[Stop]]
   * @return
   */
  def handleGeneralAkkaMessages: StateFunction = {
    case Event(ReceiveTimeout, _) =>
      context.parent ! Passivate(stopMessage = Stop)
      stay

    case Event(Stop, _) =>
      shutdown()
      stay
  }

  /**
   * This function is responsible for replying to requester with Model Not Found
   * @return
   */
  def replyWithModelNotFound: StateFunction = {
    case Event(_: RoutableToCollectionActor, _) =>
      sender() ! ModelResult.ModelNotFound
      stay
  }

  /**
   * This function is responsible for replying to requester with InvalidCommand
   * in case CollectionActor receive command or request that doesn't handle it.
   * @return
   */
  def replyWithInvalidCommand: StateFunction = {
    case Event(cmd: Any, _) =>
      sender() ! InvalidCommand(s"Unsupported command: $cmd")
      stay
  }

  /**
   * This funcation is responsible for handling Model Operations like [[ModelUpdated]] and [[ModelDeleted]]
   * @return
   */
  def handleModelOperations: StateFunction = {
    case Event(evt @ ModelUpdated(_, newModel, _), _) =>
      log.debug(s"$persistenceId Persistor received a ModelUpdated message for: $r")
      persist(evt)(_ => askMaterializerToUpdate)
      stay using ModelData(newModel)

    case Event(evt @ ModelDeleted(deletedR), _) if deletedR == r =>
      log.debug(s"$persistenceId Persistor received a DeletedModel message for: $r")
      persist(evt)(_ => askMaterializerToUpdate)
      context.parent ! Passivate(stopMessage = Stop)
      goto(WithoutModel) using Empty
  }

  /**
   * This function is responsible for handling create, update and delete requests
   * @return
   */
  def handleRequests: StateFunction = {
    case Event(request @ Create(ctx, r, body), ModelData(model)) =>
      bucket.get(r) match {
        case Some(obj) =>
          stay replying AlreadyExists(r)

        case None if r.tail.isEmpty =>
          applyCreateRequest(request, model, sender())

        case None =>
          globals.collectionActor ! GetExistenceState(r.tailR)
          goto(WaitingParentValidation) using ValidationParent(request, model, r.tailR, sender())
      }

    case Event(updateRequest: Update, ModelData(model)) =>
      bucket.get(updateRequest.r) match {
        case Some(modelObj) if updateRequest.rev > rev(modelObj.data) =>
          sender() ! OutdatedRevision
          stay

        case Some(modelObj) =>
          applyUpdateRequest(modelObj, model, updateRequest, sender())

        case None =>
          sender() ! CommandError.ObjectNotFound(updateRequest.r)
          stay
      }
  }

  /**
   * This function is responsible for handling requests between collection actors
   * @return
   */
  def handleInternalRequest: StateFunction = {
    case Event(GetExistenceState(r), _) =>
      log.debug(s"$persistenceId Persistor received a GetExistenceState message for: $r")
      sender() ! bucket.get(r).map(_ => ObjectExists(r)).getOrElse(ObjectNotFound(r))
      stay
  }

  /**
   * This function is responsible for handling [[GetExistenceState]] results
   * @return
   */
  def handleParentValidationResult: StateFunction = {
    case Event(ObjectExists(existingR), ValidationParent(request, model, parentR, requester)) if existingR == parentR =>
      unstashAll()
      validateFields(request.body, model) match {
        case JsSuccess(jsObj, _) =>
          val referenceFields = getModelReferenceFields(model)
          if (referenceFields.isEmpty) {
            createAndReply(request, jsObj, model, requester)
            goto(WithModel) using ModelData(model)
          } else {
            val expected = askAboutReferenceField(model, referenceFields, jsObj)
            if (expected.isEmpty) {
              createAndReply(request, jsObj, model, requester)
              goto(WithModel) using ModelData(model)
            } else {
              goto(WaitingReferencesValidation) using ValidationReferences(request, jsObj, model, expected, Seq.empty, requester)
            }
          }
        case error: JsError =>
          requester ! ModelValidationFailed(request.r, error)
          goto(WithModel) using ModelData(model)
      }

    case Event(ObjectNotFound(nonExistingR), ValidationParent(request, model, parentR, requester)) if nonExistingR == parentR =>
      requester ! ParentNotFound(request.r)
      unstashAll()
      goto(WithModel) using ModelData(model)
  }

  /**
   * This function is responsible handle messages that received after asked another Collection Actor about reference field
   * @return
   */
  def handleReferencesValidationResults: StateFunction = {
    case e @ Event(ObjectExists(r), ValidationReferences(_, _, _, expectedReferences, _, _)) if !expectedReferences.contains(r) =>
      log.warning("Unexpected Reference. Probably Coding bug. Event was: {}", e)
      stay

    case Event(ObjectExists(r), data @ ValidationReferences(request: Create, obj, model, expectedReferences, Nil, requester)) if expectedReferences.size == 1 =>
      createAndReply(request, obj, model, requester)
      unstashAll()
      goto(WithModel) using ModelData(model)

    case Event(ObjectExists(r), data @ ValidationReferences(request: Update, obj, model, expectedReferences, Nil, requester)) if expectedReferences.size == 1 =>
      updateAndReply(request, obj, model, requester)
      unstashAll()
      goto(WithModel) using ModelData(model)

    case Event(ObjectExists(r), data @ ValidationReferences(request, _, model, expectedReferences, invalidReferences, requester)) if expectedReferences.size == 1 =>
      val errors: Seq[(JsPath, Seq[ValidationError])] = invalidReferences.map { f =>
        __ \ f -> Seq(ValidationError("invalid.value"))
      }
      requester ! ModelValidationFailed(request.r, JsError(errors))
      unstashAll()
      goto(WithModel) using ModelData(model)

    case Event(ObjectExists(r), data @ ValidationReferences(_, _, _, expectedReferences, _, _)) =>
      stay using data.copy(expectedReferences = expectedReferences - r)

    case Event(ObjectNotFound(r), data @ ValidationReferences(request, obj, model, expectedReferences, invalidReferences, requester)) if expectedReferences.size == 1 =>
      val errors: Seq[(JsPath, Seq[ValidationError])] = (invalidReferences :+ expectedReferences(r)).map { f =>
        __ \ f -> Seq(ValidationError("invalid.value"))
      }
      requester ! ModelValidationFailed(request.r, JsError(errors))
      unstashAll()
      goto(WithModel) using ModelData(model)

    case Event(ObjectNotFound(r), data @ ValidationReferences(_, obj, model, expected, invalid, _)) =>
      stay using data.copy(
        expectedReferences = expected - r,
        invalidReferences = invalid :+ expected(r)
      )
  }

  /**
   * This function is responsible for updating bucket from Collection Events
   * @param evt
   * @param data
   */
  def updateBucket(evt: CollectionActorEvent, data: Option[JsObject] = None) =
    evt match {
      case CollectionActorEvent.Created(r, obj, modelVersion, ctx) =>
        val lastTouched = obj.keys.map(k => (k, 1l)).toMap
        bucket += r -> DataObject(obj, modelVersion, lastTouched)

      case CollectionActorEvent.Updated(r, ops, newRev, modelVersion, ctx) if data.isDefined =>
        val modelObject = DataObject(data.get, modelVersion, bucket(r).lastTouched ++ getLastTouched(ops, rev(data.get)))
        bucket += r -> modelObject

      case CollectionActorEvent.Updated(r, ops, newRev, modelVersion, ctx) =>
        throw new IllegalStateException("Cannot update state of the data was not exist!")
    }

  /**
   * read revision from object data
   * @param obj
   * @return
   */
  def rev(obj: JsObject): Revision = (obj \ "_rev").as[Revision]

  private def getLastTouched(ops: List[UpdateOp], newRev: Revision) =
    ops.foldLeft(Map.empty[FieldKey, Revision]) {
      case (lastTouched, opBody) => lastTouched + (opBody.key -> newRev)
    }

  /**
   * apply update operations on object and generate new values on object
   * @param obj
   * @param ops
   * @param reqRev
   * @return
   */
  def getUpdatedData(obj: DataObject, ops: List[UpdateOp], reqRev: Revision): JsResult[JsObject] = {
    val newObj = obj.data ++ Json.obj("_rev" -> (rev(obj.data) + 1))
    val result = ops map {
      opBody =>
        val operationPath = s"${opBody.op}.${opBody.key}"
        parseUpdateOperation(opBody, obj, reqRev, operationPath)
    }
    JsResultHelpers.merge(JsSuccess(newObj) :: result) match {
      case JsSuccess(obj, _) => JsSuccess(obj)
      case error: JsError => error
    }
  }

  def parseUpdateOperation(opBody: UpdateOp, oldObject: DataObject, reqRev: Revision, errorKey: String): JsResult[JsObject] =
    opBody match {
      case UpdateOp(UpdateCommand.Set, _, _, _) if reqRev < oldObject.lastTouched(opBody.key) =>
        JsError(__ \ errorKey, ValidationError(s"error.revision.outdated"))

      case UpdateOp(UpdateCommand.Set, _, _, _) =>
        JsSuccess(Json.obj(opBody.key -> opBody.value))

      case UpdateOp(UpdateCommand.AddNumber, _, JsNumber(v), _) =>
        oldObject.data \ opBody.key match {
          case JsNumber(originalValue) => JsSuccess(Json.obj(opBody.key -> (originalValue + v)))
          case _ => JsError(__ \ errorKey, ValidationError(s"error.non.numeric.field"))
        }

      case UpdateOp(UpdateCommand.AddNumber, _, _, _) =>
        JsError(__ \ errorKey, ValidationError(s"error.value.should.be.number"))

      case UpdateOp(_, _, _, _) =>
        JsError(__ \ errorKey, ValidationError(s"error.not.supported"))
    }

  /**
   * Shutting down the actor
   * Should save a snapshot before stopping the actor
   */
  def shutdown(): Unit = {
    saveSnapshot(bucket) //todo: maybe optimise
    context.stop(self)
  }

  /**
   * notify Materializer view to update state
   */
  def askMaterializerToUpdate = globals.materializerView ! CollectionViewMaterializer.UpdateProjection(bucketId)

  /**
   * get reference fields from model
   * @param m
   * @return
   */
  private def getModelReferenceFields(m: Model): Map[FieldKey, ReferenceField] =
    m.fields.collect {
      case (key, field @ ReferenceField(_, _, _, _)) => key -> field
    }

  /**
   * This function is responsible for ask another CollectionActors about values on reference field
   * to make sure this values refer to exist object
   * @param m
   * @param referenceFields
   * @param obj
   * @return
   */
  private def askAboutReferenceField(m: Model, referenceFields: Map[FieldKey, ReferenceField], obj: JsObject): Map[R, FieldKey] =
    referenceFields collect {
      case (f, ReferenceField(_, true, _, _)) =>
        val fValue = (obj \ f).as[R]
        globals.collectionActor ! GetExistenceState(fValue)
        fValue -> f
      case (f, ReferenceField(_, false, _, _)) if ((obj \ f).asOpt[R]).isDefined =>
        val v = (obj \ f).as[R]
        globals.collectionActor ! GetExistenceState(v)
        v -> f
    }

  /**
   * This function is responsible for validate create request, apply  create request on bucket and reply to requester
   * @param request
   * @param model
   * @param requester
   * @return
   */
  private def applyCreateRequest(request: Create, model: Model, requester: ActorRef): State =
    validateFields(request.body, model) match {
      case JsSuccess(jsObj, _) =>
        val referenceFields = getModelReferenceFields(model)
        if (referenceFields.isEmpty) {
          createAndReply(request, jsObj, model, requester)
          stay
        } else {
          val expectedReferences = askAboutReferenceField(model, referenceFields, jsObj)
          if (expectedReferences.isEmpty) {
            createAndReply(request, jsObj, model, requester)
            stay
          } else {
            goto(WaitingReferencesValidation) using ValidationReferences(request, jsObj, model, expectedReferences, Seq.empty, requester)
          }
        }
      case error: JsError =>
        requester ! ModelValidationFailed(r, error)
        stay
    }

  /**
   * This function is responsible for add new object on bucket and reply to requester
   * @param request
   * @param obj
   * @param model
   * @param requester
   */
  private def createAndReply(request: Create, obj: JsObject, model: Model, requester: ActorRef): Unit = {
    model.executeValidate(request.ctx, globals, obj) match {
      case ModelHookStatus.Succeeded =>
        val newObj = obj ++ Json.obj(Model.RevisionField -> 1l, Model.RField -> request.r)
        persistEvent(
          CollectionActorEvent.Created(request.r, newObj, model.collectionMeta.version, request.ctx),
          newObj,
          requester,
          CreateResult(request.r, newObj)
        )

      case ModelHookStatus.Terminated(code, reason) =>
        requester ! JSValidationFailed(request.r, reason) //todo fix me, needs comprehensive reason to be communicated
    }
  }

  /**
   * This function is responsible for execute js reads for fields
   * @param obj
   * @param model
   * @return
   */
  private def validateFields(obj: JsObject, model: Model): JsResult[JsObject] = {
    val (activeFields, reactiveFields) = model.fields.partition { case (_, v) => v.isInstanceOf[ValueField[_]] }
    JsResultHelpers.merge(validateActiveFields(obj, activeFields)) match {
      case JsSuccess(active: JsObject, _) =>
        val (calculatedFields, referenceFields) = reactiveFields.partition { case (_, v) => !v.isInstanceOf[ReferenceField] }
        val calculate = JsResultHelpers.merge(evaluateReactiveFields(active, calculatedFields))
        val reference = JsResultHelpers.merge(evaluateReactiveFields(obj, referenceFields))
        val reactive = JsResultHelpers.merge(List(calculate, reference))
        reactive.map(x => x ++ active)
      case error: JsError => error
    }
  }

  /**
   * This function is responsible for execute js reads for active fields
   * @param obj
   * @param fields
   * @return
   */
  private def validateActiveFields(obj: JsObject, fields: Map[FieldKey, Field[_]]): List[JsResult[JsObject]] = {
    fields.map {
      case (keyField, valueField) =>
        val fieldBranch = (__ \ keyField).json.pickBranch
        val fieldObj = obj.validate(fieldBranch).getOrElse(Json.obj())
        valueField.read(JsPath(), fieldObj)
    }.toList
  }

  /**
   * This function is responsible for execute js reads for reactive fields
   * @param obj
   * @param fields
   * @return
   */
  private def evaluateReactiveFields(obj: JsObject, fields: Map[FieldKey, Field[_]]): List[JsResult[JsObject]] = {
    fields.map {
      case (keyField, valueField) =>
        valueField.read(JsPath(), obj)
    }.toList
  }

  /**
   * handle update request and update bucket
   * @param modelObj
   * @param model
   * @param updateRequest
   * @param requester
   */
  private def applyUpdateRequest(modelObj: DataObject, model: Model, updateRequest: Request.Update, requester: ActorRef): State =
    getUpdatedData(modelObj, updateRequest.body.ops, updateRequest.rev) match {
      case JsSuccess(obj, _) =>
        validateObject(obj, model)(updateRequest.ctx) match {
          case ValidationResponse.ValidData(jsObj) =>
            val modelReferenceFields = getModelReferenceFields(model)
            val updatedReferenceField = updateRequest.body.ops collect {
              case UpdateOp(UpdateCommand.Set, f, _, _) if modelReferenceFields.contains(f) =>
                f -> modelReferenceFields(f)
            }
            if (updatedReferenceField.isEmpty) {
              updateAndReply(updateRequest, jsObj, model, requester)
              stay
            } else {
              val expectedReferences = askAboutReferenceField(model, updatedReferenceField.toMap, jsObj)
              if (expectedReferences.isEmpty) {
                updateAndReply(updateRequest, jsObj, model, requester)
                stay
              } else {
                goto(WaitingReferencesValidation) using ValidationReferences(updateRequest, jsObj, model, expectedReferences, Seq.empty, requester)
              }
            }

          case ValidationResponse.JSValidationFailed(reason) =>
            requester ! JSValidationFailed(updateRequest.r, reason)
            stay
          case ValidationResponse.ModelValidationFailed(error) =>
            requester ! ModelValidationFailed(updateRequest.r, error)
            stay
        }
      case e: JsError =>
        log.debug("Failure while validating update data: " + e)
        requester ! ValidationFailed(e)
        stay
    }

  /**
   * This function is responsible for execute preUpdate jsHooks, update bucket and reply to requester
   * @param jsObj
   * @param model
   * @param updateReq
   * @param requester
   */
  private def updateAndReply(updateReq: Request.Update, jsObj: JsObject, model: Model, requester: ActorRef): Unit = {
    persistEvent(CollectionActorEvent.Updated(updateReq.r, updateReq.body.ops, rev(jsObj),
      model.collectionMeta.version, updateReq.ctx), jsObj, requester, UpdateResult(updateReq.r, rev(jsObj)))
  }

  /**
   * validate object based on Model schema
   * @param obj
   * @param model
   * @param context
   * @return
   */
  private def validateObject(obj: JsObject, model: Model)(implicit context: RequestContext): ValidationResponse =
    validateFields(obj, model) match {
      case JsSuccess(jsObj: JsObject, _) => // Continue to JS Validation
        model.executeValidate(context, globals, jsObj) match {
          case ModelHookStatus.Succeeded => ValidationResponse.ValidData(obj)
          case ModelHookStatus.Terminated(code, reason) => ValidationResponse.JSValidationFailed(reason)
        }
      case error: JsError => ValidationResponse.ModelValidationFailed(error)
    }

  /**
   * Persist the event, update the current state and reply to requester
   * @param evt
   * @param jsObj
   */
  private def persistEvent(evt: CollectionActorEvent, jsObj: JsObject, requester: ActorRef, result: Response) = {
    persist(evt) {
      event =>
        updateBucket(event, Some(jsObj))
        askMaterializerToUpdate
        requester ! result
    }
  }

}

object CollectionActor {
  sealed trait CollectionState
  case object Initialization extends CollectionState
  case object WithoutModel extends CollectionState
  case object WithModel extends CollectionState
  case object CreatingObject extends CollectionState
  case object WaitingReferencesValidation extends CollectionState
  case object WaitingParentValidation extends CollectionState
  case object UpdatingObject extends CollectionState

  sealed trait CollectionData
  case object Empty extends CollectionData
  case class ModelData(model: Model) extends CollectionData
  case class CreatingData(obj: JsObject, model: Model) extends CollectionData
  case class ValidationReferences(request: RoutableToCollectionActor, obj: JsObject, model: Model, expectedReferences: Map[R, FieldKey], invalidReferences: Seq[FieldKey], requester: ActorRef) extends CollectionData
  case class ValidationParent(request: Create, model: Model, parentR: R, requester: ActorRef) extends CollectionData

  case object Stop

  case class GetExistenceState(r: R) extends InternalRequest

  case class State(obj: JsObject) extends Response

  trait ObjectResponse {
    def r: R
  }

  case class ObjectExists(r: R) extends ObjectResponse

  case class ObjectNotFound(r: R) extends ObjectResponse

  trait CollectionActorEvent {
    def r: R

    def context: RequestContext
  }

  object CollectionActorEvent {

    case class Created(r: R, obj: JsObject, modelVersion: ModelVersion, context: RequestContext) extends CollectionActorEvent

    case class Updated(r: R, ops: List[UpdateOp], newRev: Revision, modelVersion: ModelVersion,
      context: RequestContext) extends CollectionActorEvent

    case class Deleted(r: R, context: RequestContext) extends CollectionActorEvent
  }

  trait ValidationResponse

  object ValidationResponse {

    case class JSValidationFailed(reason: String) extends ValidationResponse

    case class ModelValidationFailed(e: JsError) extends ValidationResponse

    case class ValidData(obj: JsObject) extends ValidationResponse

  }
}
