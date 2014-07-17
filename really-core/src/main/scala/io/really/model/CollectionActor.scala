package io.really.model

import akka.actor._
import io.really._


object CollectionActor {

  /**
   * [[ObjectPersistor]] sends this message to [[CollectionActor]] if it has not been receiving any commands for a pre-defined
   * period in `really.persistence.object-persistor-timeout` (defined in milliseconds)
   * @param actor
   * @param r
   */
  case class ObjectActorIsIdle(actor: ActorRef, r: R)
}

/**
 * Collection Actor is the parent of all the persistent actors for a certain collection, the collection actor is responsible
 * for spawning up and shutting down them. The Collection Actor is also responsible for generating IDs for new objects
 */
class CollectionActor(globals: ReallyGlobals, r: R, model: Model) extends Actor with ActorLogging {
  import CollectionActor._

  var liveObjects = Map.empty[R, ActorRef]

  def receive = {
    case createCmd: io.really.model.Create =>
      //todo validate that r is skeleton and that no loopholes are there

      // create a new ID
      val newR = r / globals.quickSand.nextId()
      //todo switch to akka sharding here for clustering support
      val ref = context.actorOf(ObjectPersistor.props(globals, newR, model), newR.actorFriendlyStr)
      //adding the new actor to liveObject
      liveObjects += (newR -> ref)
      ref forward createCmd //forwarding the origin message tot he actor ref

    case ObjectActorIsIdle(actor, r) =>
      //sender is idle and we need to kill it
      liveObjects -= r
      actor ! PoisonPill //stop the sender actor, that ensures that the queue of the persistor is empty
  }
}