/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import akka.actor._
import scala.slick.driver.H2Driver.simple._
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.contrib.pattern.{ DistributedPubSubMediator, ShardRegion }
import io.really.gorilla.SubscriptionManager.ObjectSubscribed
import io.really.model.{ Model, Helpers }
import io.really._
import scala.slick.jdbc.meta.MTable
import EventLogs._

/**
 * The gorilla event centre is an actor that receives events from the Collection View Materializer
 * and store it in a persistent ordered store (H2 database)
 * It ensures that we are not storing the same revision twice for the same object (by ignoring the event)
 * publishes the event on the Gorilla PubSub asynchronously for real-time event distribution
 * @param globals
 */
class GorillaEventCenter(globals: ReallyGlobals)(implicit session: Session) extends Actor with ActorLogging {

  import GorillaEventCenter._

  val bucketID: BucketID = self.path.name

  val r: R = Helpers.getRFromBucketID(bucketID)

  def receive: Receive = handleEvent orElse handleSubscriptions

  def handleEvent: Receive = {
    case msg: PersistentEvent =>
      persistEvent(msg)

    case evt: StreamingEvent =>
      globals.mediator ! evt

    case ModelUpdatedEvent(_, model) =>
      removeOldModelEvents(model)
    // todo notify the replayers with model updates

  }

  def handleSubscriptions: Receive = {
    case NewSubscription(replyTo, rSub) =>
      val objectSubscriber = context.actorOf(globals.objectSubscriberProps(rSub))
      val replayer = markers.filter(_.r === rSub.r).firstOption match {
        case Some((_, rev)) =>
          context.actorOf(globals.replayerProps(rSub, objectSubscriber, Some(rev), session))
        case None =>
          context.actorOf(globals.replayerProps(rSub, objectSubscriber, None, session))
      }
      globals.mediator ! Subscribe(rSub.r.toString, replayer)
      objectSubscriber ! ReplayerSubscribed(replayer)
      sender() ! ObjectSubscribed(rSub, replyTo, objectSubscriber)
  }

  private def persistEvent(persistentEvent: PersistentEvent): Unit =
    persistentEvent match {
      case ev @ PersistentCreatedEvent(event) if !markers.filter(_.r === event.r).exists.run =>
        markers += (event.r, 1l)
        events += EventLog("created", event.r, 1l, event.modelVersion, event.obj,
          event.context.auth, None)
        //publish on the collection R
        globals.mediator ! DistributedPubSubMediator.Publish(event.r.noId.toString, ev)
      case ev @ PersistentUpdatedEvent(event, obj) =>
        val markerQuery = markers.filter(_.r === event.r)
        markerQuery.firstOption match {
          case Some(_) => markerQuery.update((event.r, event.rev))
          case None => markers += (event.r, event.rev)
        }
        events += EventLog("updated", event.r, event.rev, event.modelVersion, obj,
          event.context.auth, Some(event.ops))
        globals.mediator ! DistributedPubSubMediator.Publish(event.r.toString, ev)
      case event =>
        //ignore this event as the event already stored
        log.warning(s"Ignore this event ($event) as the event already stored or not supported")
    }

  private def removeOldModelEvents(model: Model) =
    events.filter(_.modelVersion < model.collectionMeta.version).delete
}

object GorillaEventCenter {
  // the query interface for the log table
  val events: TableQuery[EventLogs] = TableQuery[EventLogs]
  val markers: TableQuery[EventLogMarkers] = TableQuery[EventLogMarkers]

  /**
   * Create the events table
   * @param session
   * @return
   */
  def initializeDB()(implicit session: Session) =
    if (MTable.getTables(EventLogs.tableName).list.isEmpty) {
      events.ddl.create
      markers.ddl.create
    }

  case class ReplayerSubscribed(replayer: ActorRef)

}

class GorillaEventCenterSharding(config: ReallyConfig) {

  implicit val implicitConfig = config

  val maxShards = config.Sharding.maxShards

  /**
   * ID Extractor for Akka Sharding extension
   * ID is the BucketId
   */
  val idExtractor: ShardRegion.IdExtractor = {
    case req: RoutableToGorillaCenter => Helpers.getBucketIDFromR(req.r) -> req
    case modelEvent: ModelEvent => modelEvent.bucketID -> modelEvent
  }

  /**
   * Shard Resolver for Akka Sharding extension
   */
  val shardResolver: ShardRegion.ShardResolver = {
    case req: RoutableToGorillaCenter => (Helpers.getBucketIDFromR(req.r).hashCode % maxShards).toString
    case modelEvent: ModelEvent => (modelEvent.bucketID.hashCode % maxShards).toString
  }
}
