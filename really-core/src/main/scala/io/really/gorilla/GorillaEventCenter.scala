/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import akka.actor.{ ActorLogging, Actor }
import akka.contrib.pattern.ShardRegion
import play.api.libs.json.{ Json }
import io.really.model.{ Model, Helpers }
import io.really._

import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable

/**
 * The gorilla event centre is an actor that receives events from the Collection View Materializer
 * and store it in a persistent ordered store (MongoDB per collection (R) capped collection)
 * and return on success a confirmation EventStored to the view materializer to proceed with he next message.
 * It ensures that we are not storing the same revision twice for the same object (by ignoring the event)
 * while confirming that it's stored (faking store).
 * publishes the event on the Gorilla PubSub asynchronously for real-time event distribution
 * @param globals
 */
class GorillaEventCenter(globals: ReallyGlobals)(implicit session: Session) extends Actor with ActorLogging {
  import GorillaEventCenter._

  val bucketID: BucketID = self.path.name

  val r: R = Helpers.getRFromBucketID(bucketID)

  private[this] val config = globals.config.EventLogStorage

  def receive: Receive = handleEvent

  def handleEvent: Receive = {
    case msg: PersistentEvent =>
      sender ! persistEvent(msg)
    //todo pass it to the pubsub

    case evt: StreamingEvent =>
    //todo pass it silently to the pubsub

    case ModelUpdatedEvent(_, model) =>
      removeOldModelEvents(model)
    //todo notify the replayers with model updates
  }

  private def persistEvent(persistentEvent: PersistentEvent): GorillaLogResponse =
    persistentEvent match {
      case PersistentCreatedEvent(event) =>
        events += EventLog("create", event.r.toString, 1l, event.modelVersion, Json.stringify(event.obj),
          Json.stringify(UserInfo.fmt.writes(event.context.auth)), None)
        EventStored
      case PersistentUpdatedEvent(event, obj) =>
        events += EventLog("update", event.r.toString, event.rev, event.modelVersion, Json.stringify(obj),
          Json.stringify(UserInfo.fmt.writes(event.context.auth)), Some(Json.stringify(Json.toJson(event.ops))))
        EventStored
      case _ => GorillaLogError.UnsupportedEvent
    }

  private def removeOldModelEvents(model: Model) =
    events.filter(_.ModelVersion < model.collectionMeta.version).delete
}

object GorillaEventCenter {
  // the query interface for the log table
  val events: TableQuery[EventLogs] = TableQuery[EventLogs]

  /**
   * Create the events table
   * @param session
   * @return
   */
  def initializeDB()(implicit session: Session) =
    if (MTable.getTables(EventLogs.tableName).list.isEmpty) {
      events.ddl.create
    }

}

class GorillaEventCenterSharding(config: ReallyConfig) {

  implicit val implicitConfig = config

  val maxShards = config.Sharding.maxShards

  /**
   * ID Extractor for Akka Sharding extension
   * ID is the BucketId
   */
  val idExtractor: ShardRegion.IdExtractor = {
    case persistentEvent: PersistentEvent => Helpers.getBucketIDFromR(persistentEvent.event.r) -> persistentEvent
    case modelEvent: ModelEvent => modelEvent.bucketID -> modelEvent
    //todo handle streaming events
  }

  /**
   * Shard Resolver for Akka Sharding extension
   */
  val shardResolver: ShardRegion.ShardResolver = {
    case persistentEvent: PersistentEvent => (Helpers.getBucketIDFromR(persistentEvent.event.r).hashCode % maxShards).toString
    case modelEvent: ModelEvent => (modelEvent.bucketID.hashCode % maxShards).toString
    //todo handle streaming events
  }
}
