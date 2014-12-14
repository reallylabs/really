/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor._
import akka.persistence.{ RecoveryFailure, SnapshotOffer }
import akka.contrib.pattern.ShardRegion.Passivate
import akka.persistence.{ RecoveryCompleted, PersistentActor }
import akka.util.Timeout
import _root_.io.really.CommandError._
import _root_.io.really.Result.{ CreateResult, UpdateResult }
import io.really._
import _root_.io.really.Request._
import akka.pattern.{ AskTimeoutException, ask, pipe }
import _root_.io.really.js.JsResultHelpers
import _root_.io.really.model.persistent.ModelRegistry
import ModelRegistry.CollectionActorMessage.GetModel
import _root_.io.really.model.persistent.ModelRegistry.ModelOperation.{ ModelCreated, ModelUpdated, ModelDeleted }
import ModelRegistry.ModelResult
import _root_.io.really.model.materializer.CollectionViewMaterializer
import play.api.data.validation.ValidationError
import play.api.libs.json._
import _root_.io.really.protocol.{ UpdateOp, UpdateCommand }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.control.NonFatal

/**
 * Collection Actor is responsible for:
 * Handling group of objects falls in a specific bucket for a specific collection
 * Handling Create command
 * Handling Update Command
 * Handling Delete Command
 */
class CollectionActor(globals: ReallyGlobals) extends PersistentActor with ActorLogging with Stash {

  private var t1 = 0l

  import CollectionActor._

  context.setReceiveTimeout(globals.config.CollectionActorConfig.idleTimeout)

  private[this] var state: Buckets = Map.empty

  val bucketID: BucketID = self.path.name
  override val persistenceId = bucketID

  val r: R = Helpers.getRFromBucketID(bucketID)

  log.debug(s"$persistenceId Persistor started with BucketID: $bucketID and R: $r")

  override def preStart() = {
    t1 = System.currentTimeMillis()
    super.preStart()
  }

  override def receiveRecover: Receive = {
    case evt: Event.Created =>
      log.debug(s"$persistenceId Persistor received a Create replay event: $evt")
      updateState(evt)

    case evt @ Event.Updated(r, ops, newRev, modelVersion, ctx) if state.get(r).isDefined =>
      log.debug(s"$persistenceId Persistor received an Update replay event: $evt")
      val modelObj = state(r)
      getUpdatedData(modelObj, ops, rev(modelObj.data)) match {
        case JsSuccess(obj, _) => updateState(evt, Some(obj))
        case e: JsError =>
          log.error(s"An error occurred while constructing the updating object with this error $e")
        //TODO: mark the object to be corrupted in the future
      }

    case evt: Event =>
      log.warning(s"[[NOT IMPLEMENTED]] Got EVENT: $evt")
    //TODO: handle other events

    //TODO handle saveSnapshot()
    case SnapshotOffer(_, snapshot: Map[R @unchecked, DataObject @unchecked]) => state = snapshot

    case RecoveryCompleted =>
      log.debug(s"$persistenceId Persistor took to recover was {}ms", System.currentTimeMillis - t1)
      implicit val timeout = Timeout(globals.config.CollectionActorConfig.waitForModel)
      log.debug(s"$persistenceId Persistor received a Recovery Complete message")
      val f = (globals.modelRegistry ? GetModel(r, self)).mapTo[ModelResult]
      f.recoverWith {
        case e: AskTimeoutException =>
          log.debug(s"$persistenceId Persistor timed out waiting for the model object")
          Future successful ModelResult.ModelFetchError(r, s"Request to fetch the model timed out for R: $r")
        case NonFatal(e) =>
          log.error(e, s"Unexpected error while getting the model instance for persistor $persistenceId")
          Future successful ModelResult.ModelFetchError(r, s"Request to fetch the model failed for R: $r with error: $e")
      } pipeTo self

    case RecoveryFailure(cause) =>
      log.error(s"$persistenceId Persistor failed to recover this cause: $cause")
      context.parent ! Passivate(stopMessage = Stop)
  }

  def updateState(evt: Event, data: Option[JsObject] = None) =
    evt match {
      case Event.Created(r, obj, modelVersion, ctx) =>
        val lastTouched = obj.keys.map(k => (k, 1l)).toMap
        state += r -> DataObject(obj, modelVersion, lastTouched)

      case Event.Updated(r, ops, newRev, modelVersion, ctx) if data.isDefined =>
        val modelObject = DataObject(data.get, modelVersion, state(r).lastTouched ++ getLastTouched(ops, rev(data.get)))
        state += r -> modelObject

      case Event.Updated(r, ops, newRev, modelVersion, ctx) =>
        throw new IllegalStateException("Cannot update state of the data was not exist!")

    }

  /**
   * Shutting down the actor
   * Should save a snapshot before stopping the actor
   */
  def shutdown(): Unit = {
    saveSnapshot(state) //todo: maybe optimise
    context.stop(self)
  }

  def replyToInvalidCommand(cmd: Any) = sender() ! InvalidCommand(cmd.toString)

  override def receiveCommand: Receive = waitingModel

  def invalid: Receive = {
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = Stop)

    case Stop =>
      shutdown()

    case _: RoutableToCollectionActor =>
      sender() ! ModelResult.ModelNotFound

    case cmd: Any =>
      replyToInvalidCommand(cmd)
  }

  def withModel(m: Model): Receive = {
    case evt @ ModelUpdated(_, newModel, _) =>
      log.debug(s"$persistenceId Persistor received a ModelUpdated message for: $r")
      persist(evt) {
        event =>
          globals.materializerView ! CollectionViewMaterializer.UpdateProjection(bucketID)
      }
      context.become(withModel(newModel))

    case ModelDeleted(deletedR) if deletedR == r =>
      //Sanity check, ModelRegistryRouter should only send me ModelDeleted for my own R
      log.debug(s"$persistenceId Persistor received a DeletedModel message for: $r")
      globals.materializerView ! CollectionViewMaterializer.UpdateProjection(bucketID)
      context.become(invalid)
      context.parent ! Passivate(stopMessage = Stop)

    //For testing purposes ONLY!
    case GetState(r) =>
      log.debug(s"$persistenceId Persistor received a GetState message for: $r")
      state.get(r).map(obj =>
        sender() ! State(obj.data)).getOrElse {
        sender() ! ObjectNotFound(r)
      }

    case GetExistenceState(r) =>
      log.debug(s"$persistenceId Persistor received a GetExistenceState message for: $r")
      state.get(r).map(_ =>
        sender() ! ObjectExists).getOrElse {
        sender() ! ObjectNotFound(r)
      }

    case req @ Create(ctx, r, body) =>
      state.get(r) match {
        case Some(obj) =>
          sender() ! AlreadyExists(r)
        case None if r.tail.isEmpty =>
          sender() ! applyCreate(Create(ctx, r, body), m)
        case None =>
          val catchTheSender = sender()
          implicit val timeout = Timeout(globals.config.CollectionActorConfig.waitForObjectState)
          (globals.collectionActor ? GetExistenceState(r.tailR)) map {
            case ObjectExists =>
              catchTheSender ! applyCreate(Create(ctx, r, body), m)
            case e: ObjectNotFound =>
              catchTheSender ! ParentNotFound(r)
          } recover {
            case _ =>
              catchTheSender ! InternalServerError(s"Server encountered a problem while checking for parent object existence, for request: $req")
          }
      }

    case updateRequest: Update =>
      state.get(updateRequest.r) match {
        case Some(modelObj) if updateRequest.rev > rev(modelObj.data) =>
          sender() ! OutdatedRevision
        case Some(modelObj) =>
          sender() ! applyUpdate(modelObj, m, updateRequest)
        case None => sender() ! ObjectNotFound(updateRequest.r)
      }

    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = Stop)

    case Stop =>
      shutdown()

    case cmd: Any =>
      replyToInvalidCommand(cmd)
  }

  def waitingModel: Receive = {
    case ModelResult.ModelNotFound =>
      log.debug(s"$persistenceId Persistor couldn't find the model for r: $r")
      context.parent ! Passivate(stopMessage = Stop)
      unstashAll()
      context.become(invalid)
    case evt @ ModelResult.ModelObject(m, refL) =>
      log.debug(s"$persistenceId Persistor found the model for r: $r")
      persist(ModelCreated(m.r, m, refL)) {
        event =>
          globals.materializerView ! CollectionViewMaterializer.UpdateProjection(bucketID)
      }
      unstashAll()
      context.become(withModel(m))
    case _ =>
      stash()
  }

  private def applyUpdate(modelObj: DataObject, model: Model, updateReq: Request.Update): Response =
    getUpdatedData(modelObj, updateReq.body.ops, updateReq.rev) match {
      case JsSuccess(obj, _) =>
        validateObject(obj, model)(updateReq.ctx) match {
          case ValidationResponse.ValidData(jsObj) =>
            persistEvent(Event.Updated(updateReq.r, updateReq.body.ops, rev(jsObj),
              model.collectionMeta.version, updateReq.ctx), jsObj)
            UpdateResult(updateReq.r, rev(jsObj))
          case ValidationResponse.JSValidationFailed(reason) =>
            JSValidationFailed(updateReq.r, reason)
          case ValidationResponse.ModelValidationFailed(error) =>
            ModelValidationFailed(updateReq.r, error)
        }
      case e: JsError =>
        log.debug("Failure while validating update data: " + e)
        ValidationFailed(e)
    }

  /**
   * Persist the event and update the current state
   * @param evt
   * @param jsObj
   */
  private def persistEvent(evt: Event, jsObj: JsObject) = {
    persist(evt) {
      event =>
        updateState(event, Some(jsObj))
        globals.materializerView ! CollectionViewMaterializer.UpdateProjection(bucketID)
    }
  }

  private def validateObject(obj: JsObject, model: Model)(implicit context: RequestContext): ValidationResponse =
    validateFields(obj, model) match {
      case JsSuccess(jsObj: JsObject, _) => // Continue to JS Validation
        model.executeValidate(context, globals, jsObj) match {
          case ModelHookStatus.Succeeded => ValidationResponse.ValidData(obj)
          case ModelHookStatus.Terminated(code, reason) => ValidationResponse.JSValidationFailed(reason)
        }
      case error: JsError => ValidationResponse.ModelValidationFailed(error)
    }

  private def getLastTouched(ops: List[UpdateOp], newRev: Revision) =
    ops.foldLeft(Map.empty[FieldKey, Revision]) {
      case (lastTouched, opBody) => lastTouched + (opBody.key -> newRev)
    }

  private def applyCreate(create: Create, model: Model): Response =
    validateFields(create.body, model) match {
      case JsSuccess(jsObj, _) => // Continue to JS Validation
        model.executeValidate(create.ctx, globals, jsObj) match {
          case ModelHookStatus.Succeeded =>
            val newObj = jsObj ++ Json.obj("_rev" -> 1l, "_r" -> create.r.toString)
            persistEvent(Event.Created(create.r, newObj, model.collectionMeta.version, create.ctx), newObj)
            CreateResult(create.r, newObj)
          case ModelHookStatus.Terminated(code, reason) =>
            JSValidationFailed(create.r, reason) //todo fix me, needs comprehensive reason to be communicated
        }
      case error: JsError =>
        ModelValidationFailed(create.r, error)
    }
}

object CollectionActor {

  case object Stop

  case class GetState(r: R) extends InternalRequest

  case class GetExistenceState(r: R) extends InternalRequest

  case class State(obj: JsObject) extends Response

  case object ObjectExists extends Response

  trait Event {
    def r: R

    def context: RequestContext
  }

  object Event {

    case class Created(r: R, obj: JsObject, modelVersion: ModelVersion, context: RequestContext) extends Event

    case class Updated(r: R, ops: List[UpdateOp], newRev: Revision, modelVersion: ModelVersion,
      context: RequestContext) extends Event

    case class Deleted(r: R, context: RequestContext) extends Event
  }

  trait ValidationResponse

  object ValidationResponse {

    case class JSValidationFailed(reason: String) extends ValidationResponse

    case class ModelValidationFailed(e: JsError) extends ValidationResponse

    case class ValidData(obj: JsObject) extends ValidationResponse

  }

  def rev(obj: JsObject): Revision = (obj \ "_rev").as[Revision]

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
      case UpdateOp(UpdateCommand.AddNumber, _, _, _) => JsError(__ \ errorKey, ValidationError(s"error.value.should.be.number"))
      case UpdateOp(_, _, _, _) => JsError(__ \ errorKey, ValidationError(s"error.not.supported"))
    }

  private def validateActiveFields(obj: JsObject, fields: Map[FieldKey, Field[_]]): List[JsResult[JsObject]] = {
    fields.map {
      case (keyField, valueField) =>
        val fieldBranch = (__ \ keyField).json.pickBranch
        val fieldObj = obj.validate(fieldBranch).getOrElse(Json.obj())
        valueField.read(JsPath(), fieldObj)
    }.toList
  }

  private def evaluateReActiveFields(obj: JsObject, fields: Map[FieldKey, Field[_]]): List[JsResult[JsObject]] = {
    fields.map {
      case (keyField, valueField) =>
        valueField.read(JsPath(), obj)
    }.toList
  }

  private def validateFields(obj: JsObject, model: Model): JsResult[JsObject] = {
    val (activeFields, reactiveFields) = model.fields.partition { case (_, v) => v.isInstanceOf[ValueField[_]] }
    JsResultHelpers.merge(validateActiveFields(obj, activeFields)) match {
      case JsSuccess(active: JsObject, _) =>
        val calculated = JsResultHelpers.merge(evaluateReActiveFields(active, reactiveFields))
        calculated.map(x => x ++ active)
      case error: JsError => error
    }

  }
}
