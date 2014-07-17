package io.really.model

import akka.actor._
import akka.persistence._
import io.really._
import play.api.libs.json._
import scala.concurrent.duration.Duration

object ObjectPersistor {
  def props(globals: ReallyGlobals, r: R, model: Model): Props = {
    require(r.isObject, "R should refer to an object not a collection")
    Props(new ObjectPersistor(globals, r, model))
  }
}

class ObjectPersistor private (globals: ReallyGlobals, r: R, model: Model) extends PersistentActor with ActorLogging {

  var t1 = 0l

  override def preStart() = {
    t1 = System.currentTimeMillis()
    super.preStart()

  }

  override def persistenceId = r.toString

  var state: Option[ModelObject] = None

  def rev = state.map(model => (model.data \ "_r").as[Long]).getOrElse(0l)

  private def updateState(evt: Event) = {
    state match {
      case Some(s) => state = Some(s.updateState(evt, model.collectionMeta.version))
      case _ => throw new IllegalStateException("Cannot update state of model object if not was not created!")
    }
    log.debug("State changes, State now is {}", state)
  }

  /**
   * Handling [[io.really.model.Command]] messages
   * @param cmd any subtype of [[Command]]
   */
  def applyCommand(cmd: Command) = (state, cmd) match {
    case (Some(_), createCmd: Create) => //we have a problem, this object already exists (FAIL)
      log.debug("trying to create an already existing object")
      sender() ! AlreadyExists(r)
    case(Some(state), GetState) =>
      sender() ! State(state.data, model.collectionMeta.version)
    case (Some(s), cmd) => //todo handle the other commands (SUCCESS)
      log.warning("[[NOT IMPLEMENTED]], having a state and getting a command: {}", cmd)
    case (None, Create(ctx, obj)) => //create and we don't have a state (SUCCESS)
      /*
      todo: - Validate obj according to scheme sent in (model instance)
       */
      val newObj = obj ++ Json.obj("_rev" -> 1l, "_r" -> r.toString)
      model.executeValidate(ctx, newObj) match {
        case ModelHookStatus.Succeeded =>
          persist(Created(newObj, model.collectionMeta.version)) {
            event =>
              state = Some(ModelObject(newObj, model.collectionMeta.version))
              sender() ! ObjectCreated(newObj)
            //todo: publish the event on the Event Stream
          }
        case ModelHookStatus.Terminated(code, reason) =>
          sender() ! ValidationFailed(reason) //todo fix me, needs comprehensive reason to be communicated
      }
    case (None, cmd) => // receiving other commands and we do not have state (FAIL)
      log.error("Got a command while our state is None, {}", cmd)
    case e =>
      log.info("Cannot send command {} in the FRESH state", e)
  }

  def receiveRecover = {
    case Created(obj, version) =>
      log.debug("Time to CREATED was {}ms", System.currentTimeMillis - t1)
      state = Some(ModelObject(obj, version))

    case evt: Event =>
      log.warning(s"[[NOT IMPLEMENTED]] Got EVENT: $evt")
      //todo: handle other events
      updateState(evt)
    case SnapshotOffer(_, snapshot: ModelObject) => state = Some(snapshot)
    case RecoveryFailure(cause) =>
      log.warning("Failed to recover this persistor: {}", cause)
    //todo: should die?

    case RecoveryCompleted =>
      log.debug("Time to recover was {}ms", System.currentTimeMillis - t1)
      //ready to serve commands
      context.setReceiveTimeout(globals.config.persistence.objectActorTimeout)
      log.info("Persistor recovery completed, state is: {}", state)
    case e => log.warning("unknown event was received during recover: {}", e)
  }

  def receiveCommand = {
    case c: Command =>
      log.debug(s"Got Command: $c")
      applyCommand(c)
    case ReceiveTimeout =>
      //object is idle
      log.debug("Persistor {} is idle, requesting to be terminated", r)
      //todo check if needs to be updated for sharding
      context.parent ! CollectionActor.ObjectActorIsIdle(self, r)
      //todo discuss if this is a wise decision or not (for sending [[ObjectActorIsIde]] only once)
      context.setReceiveTimeout(Duration.Undefined)
    case e =>
      log.error("Unknown Command: {}", e)
  }

  override def postStop() = {
    log.debug("Persistor {} was stopped", r)
    super.postStop()
  }
}