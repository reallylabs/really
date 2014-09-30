package io.really.model

import akka.actor._
import akka.persistence.{RecoveryFailure, SnapshotOffer}
import akka.contrib.pattern.ShardRegion.Passivate
import akka.persistence.{RecoveryCompleted, PersistentActor}
import akka.util.Timeout
import io.really.CommandError._
import io.really.Result.{CreateResult, UpdateResult}
import io.really._
import io.really.Request._
import akka.pattern.{AskTimeoutException, ask, pipe}
import io.really.js.JsResultHelpers
import io.really.model.ModelRegistryRouter.CollectionActorMessage.GetModel
import io.really.model.ModelRegistryRouter.ModelOperation.ModelUpdated
import io.really.model.ModelRegistryRouter.ModelOperation.ModelDeleted
import io.really.model.ModelRegistryRouter.ModelResult
import play.api.data.validation.ValidationError
import play.api.libs.json._
import io.really.protocol.{UpdateOp, UpdateCommand, FieldSnapshot}
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

    case evt@Event.Updated(r, ops, revision, modelVersion, ctx) if state.get(r).isDefined =>
      log.debug(s"$persistenceId Persistor received an Update replay event: $evt")
      getUpdatedData(state(r), ops, revision) match {
        case JsSuccess(obj, _) => updateState(evt, Some(obj))
        case e: JsError =>
          log.error(s"An error occurred while constructing the updating object with this error $e")
        //TODO: mark the object to be corrupted in the future
      }

    case evt: Event =>
      log.warning(s"[[NOT IMPLEMENTED]] Got EVENT: $evt")
    //TODO: handle other events

    //TODO handle saveSnapshot()
    case SnapshotOffer(_, snapshot: Buckets) => state = snapshot

    case RecoveryCompleted =>
      log.debug(s"$persistenceId Persistor took to recover was {}ms", System.currentTimeMillis - t1)
      implicit val timeout = Timeout(globals.config.CollectionActorConfig.waitForModel)
      log.debug(s"$persistenceId Persistor received a Recovery Complete message")
      val f = (globals.modelRegistryRouter ? GetModel(r)).mapTo[ModelResult]
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
        state += r -> ModelObject(obj, modelVersion, lastTouched)

      case Event.Updated(r, ops, revision, modelVersion, ctx) if data.isDefined =>
        val modelObject = ModelObject(data.get, modelVersion, state(r).lastTouched ++ getLastTouched(ops, rev(data.get)))
        state += r -> modelObject

      case Event.Updated(r, ops, revision, modelVersion, ctx) =>
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

  def replyToInvalidCommand(cmd: Any) = sender() ! InvalidCommand(s"Unsupported command: $cmd")

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
    case ModelUpdated(_, newModel) =>
      log.debug(s"$persistenceId Persistor received a ModelUpdated message for: $r")
      context.become(withModel(newModel))

    case ModelDeleted(deletedR) if deletedR == r =>
      //Sanity check, ModelRegistryRouter should only send me ModelDeleted for my own R
      log.debug(s"$persistenceId Persistor received a DeletedModel message for: $r")
      context.become(invalid)
      context.parent ! Passivate(stopMessage = Stop)

    //For testing purposes ONLY!
    case GetState(r) =>
      log.debug(s"$persistenceId Persistor received a GetState message for: $r")
      state.get(r).map(obj =>
        sender() ! State(obj.data)
      ).getOrElse {
        sender() ! ObjectNotFound
      }

    case GetExistenceState(r) =>
      log.debug(s"$persistenceId Persistor received a GetExistenceState message for: $r")
      state.get(r).map(_ =>
        sender() ! ObjectExists
      ).getOrElse {
        sender() ! ObjectNotFound
      }

    case req@Create(ctx, r, body) =>
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
            case ObjectNotFound =>
              catchTheSender ! ParentNotFound(403, "Object parent doesn't exist")
          } recover {
            case _ => catchTheSender ! InternalServerError(500,
              s"Server encountered a problem while checking for parent object existence, for request: $req")
          }
      }

    case updateRequest: Update =>
      state.get(updateRequest.r) match {
        case Some(modelObj) => sender() ! applyUpdate(modelObj, m, updateRequest)
        case None => sender() ! ObjectNotFound
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
    case ModelResult.ModelObject(m) =>
      log.debug(s"$persistenceId Persistor found the model for r: $r")
      unstashAll()
      context.become(withModel(m))
    case _ =>
      stash()
  }

  private def applyUpdate(modelObj: ModelObject, model: Model, updateReq: Request.Update): Response =
    getUpdatedData(modelObj, updateReq.body.ops, updateReq.rev) match {
      case JsSuccess(obj, _) =>
        validateObject(obj, model)(updateReq.ctx) match {
          case ValidationResponse.ValidData(jsObj) =>
            persistEvent(Event.Updated(updateReq.r, updateReq.body.ops, updateReq.rev,
              model.collectionMeta.version, updateReq.ctx), jsObj)
            UpdateResult(getUpdateSnapshots(obj, updateReq.body.ops), rev(jsObj))
          case ValidationResponse.JSValidationFailed(reason) => JSValidationFailed(reason)
          case ValidationResponse.ModelValidationFailed(error) => ModelValidationFailed(error)
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
  private def persistEvent(evt: Event, jsObj: JsObject) =
    persist(evt) {
      event =>
        updateState(event, Some(jsObj))
      //todo: publish the event on the Event Stream
    }


  private def validateObject(obj: JsObject, model: Model)(implicit context: RequestContext): ValidationResponse =
    validateFields(obj, model) match {
      case JsSuccess(jsObj: JsObject, _) => // Continue to JS Validation
        model.executeValidate(context, jsObj) match {
          case ModelHookStatus.Succeeded => ValidationResponse.ValidData(obj)
          case ModelHookStatus.Terminated(code, reason) => ValidationResponse.JSValidationFailed(reason)
        }
      case error: JsError => ValidationResponse.ModelValidationFailed(error)
    }

  private def getLastTouched(ops: List[UpdateOp], newRev: Revision) =
    ops.foldLeft(Map.empty[FieldKey, Revision]) {
      case (lastTouched, opBody) => lastTouched + (opBody.key -> newRev)
    }

  private def getUpdateSnapshots(obj: JsObject, ops: List[UpdateOp]): List[FieldSnapshot] =
    ops.foldLeft(List.empty[FieldSnapshot]) {
      case (snapshots, opBody) =>
        FieldSnapshot(opBody.key, obj \ opBody.key) :: snapshots
    }

  private def applyCreate(create: Create, model: Model): Response =
    validateFields(create.body, model) match {
      case JsSuccess(jsObj, _) => // Continue to JS Validation
        model.executeValidate(create.ctx, jsObj) match {
          case ModelHookStatus.Succeeded =>
            val newObj = jsObj ++ Json.obj("_rev" -> 1l, "_r" -> create.r.toString)
            persist(Event.Created(create.r, newObj, model.collectionMeta.version, create.ctx)) {
              event =>
                updateState(event)
              //todo: publish the event on the Event Stream
            }
            CreateResult(newObj)
          case ModelHookStatus.Terminated(code, reason) =>
            JSValidationFailed(reason) //todo fix me, needs comprehensive reason to be communicated
        }
      case error: JsError =>
        ModelValidationFailed(error)
    }
}

object CollectionActor {

  case object Stop

  case class GetState(r: R) extends InternalRequest

  case class GetExistenceState(r: R) extends InternalRequest

  case class State(obj: JsObject) extends Response

  case object ObjectExists extends Response


  trait Event {
    def context: RequestContext
  }

  object Event {

    case class Created(r: R, obj: JsObject, modelVersion: ModelVersion, ctx: RequestContext) extends Event {
      val context = ctx
    }

    case class Updated(r: R, ops: List[UpdateOp], rev: Revision, modelVersion: ModelVersion, ctx: RequestContext) extends Event {
      val context = ctx
    }

  }

  trait ValidationResponse

  object ValidationResponse {

    case class JSValidationFailed(reason: String) extends ValidationResponse

    case class ModelValidationFailed(e: JsError) extends ValidationResponse

    case class ValidData(obj: JsObject) extends ValidationResponse

  }

  def rev(obj: JsObject): Revision = (obj \ "_rev").as[Revision]

  def getUpdatedData(obj: ModelObject, ops: List[UpdateOp], revision: Revision): JsResult[JsObject] = {
    val newObj = obj.data ++ Json.obj("_rev" -> (rev(obj.data) + 1))
    val result = ops map {
      opBody =>
        val operationPath = s"${opBody.op}.${opBody.key}"
        if (revision < obj.lastTouched(opBody.key)) JsError(__ \ operationPath, ValidationError(s"error.revision.outdated"))
        else
          parseUpdateOperation(opBody, obj.data, operationPath)
    }
    JsResultHelpers.merge(JsSuccess(newObj) :: result) match {
      case JsSuccess(obj, _) => JsSuccess(obj)
      case error: JsError => error
    }
  }

  def parseUpdateOperation(opBody: UpdateOp, oldObject: JsObject, errorKey: String): JsResult[JsObject] =
    opBody match {
      case UpdateOp(UpdateCommand.Set, _, _, _) => JsSuccess(Json.obj(opBody.key -> opBody.value))
      case UpdateOp(UpdateCommand.AddNumber, _, JsNumber(v), _) =>
        oldObject \ opBody.key match {
          case JsNumber(originalValue) => JsSuccess(Json.obj(opBody.key -> (originalValue + v)))
          case _ => JsError(__ \ errorKey, ValidationError(s"error.non.numeric.field"))
        }
      case UpdateOp(UpdateCommand.AddNumber, _, _, _) => JsError(__ \ errorKey, ValidationError(s"error.value.should.be.number"))
      case UpdateOp(_, _, _, _) => JsError(__ \ errorKey, ValidationError(s"error.not.supported"))
    }

  private def validateActiveFields(obj: JsObject, fields: Map[FieldKey, Field[_]]): List[JsResult[JsObject]] = {
    fields.map {
      case (keyField, valueField) =>
        val fieldBranch= (__ \ keyField).json.pickBranch
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
    val (activeFields, reactiveFields) = model.fields.partition { case (_, v) => v.isInstanceOf[ValueField[_]]}
    JsResultHelpers.merge(validateActiveFields(obj, activeFields)) match {
      case JsSuccess(active: JsObject, _) =>
        val calculated = JsResultHelpers.merge(evaluateReActiveFields(active, reactiveFields))
        calculated.map(x => x ++ active)
      case error: JsError => error
    }

  }
}