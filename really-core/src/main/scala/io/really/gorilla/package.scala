/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import _root_.io.really.model._
import _root_.io.really.protocol.UpdateOp
import _root_.io.really.model.CollectionActor.CollectionActorEvent
import play.api.libs.json.{ Json, JsObject }

package object gorilla {

  type SubscriptionID = String
  type PushEventType = String

  trait PersistentEvent {
    def event: CollectionActorEvent
  }

  case class PersistentCreatedEvent(event: CollectionActorEvent.Created) extends PersistentEvent

  case class PersistentUpdatedEvent(event: CollectionActorEvent.Updated, obj: JsObject) extends PersistentEvent

  case class PersistentDeletedEvent(event: CollectionActorEvent.Deleted) extends PersistentEvent

  trait ModelEvent {
    def bucketID: BucketID
  }

  case class ModelUpdatedEvent(bucketID: BucketID, model: Model) extends ModelEvent

  case class ModelDeletedEvent(bucketID: BucketID) extends ModelEvent

  //Todo define the streaming event
  case class StreamingEvent(r: R)

  trait GorillaLogResponse

  trait GorillaLogEntry

  case class GorillaLogCreatedEntry(event: String, r: R, obj: JsObject, rev: Revision,
    modelVersion: ModelVersion, userInfo: UserInfo) extends GorillaLogEntry

  case class GorillaLogUpdatedEntry(event: String, r: R, obj: JsObject, rev: Revision,
    modelVersion: ModelVersion, userInfo: UserInfo, ops: List[UpdateOp]) extends GorillaLogEntry

  /*
  * Represent implicit JSON Format for GorillaLogUpdatedEntry
  */
  object GorillaLogUpdatedEntry {
    implicit val fmt = Json.format[GorillaLogUpdatedEntry]
  }

  /*
  * Represent implicit JSON Format for GorillaLogCreatedEntry
  */
  object GorillaLogCreatedEntry {
    implicit val fmt = Json.format[GorillaLogCreatedEntry]
  }

  case object EventStored extends GorillaLogResponse

  trait GorillaLogError extends GorillaLogResponse

  object GorillaLogError {

    case object UnsupportedEvent extends GorillaLogError

    case class UnexpectedError(reason: String) extends GorillaLogError

  }

  case class RSubscription(ctx: RequestContext, cid: CID, r: R, fields: Set[FieldKey])
  case class RoomSubscription(ctx: RequestContext, cid: CID, r: R)

}