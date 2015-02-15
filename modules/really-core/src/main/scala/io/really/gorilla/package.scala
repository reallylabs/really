/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import _root_.io.really.model._
import _root_.io.really.protocol.UpdateOp
import _root_.io.really.model.CollectionActor.CollectionActorEvent
import akka.actor.ActorRef
import play.api.libs.json.{ Json, JsObject }

package object gorilla {

  type SubscriptionID = String
  type PushEventType = String

  case class RSubscription(ctx: RequestContext, r: R, fields: Set[FieldKey], rev: Revision,
    requestDelegate: ActorRef, pushChannel: ActorRef)

  case class RoomSubscription(ctx: RequestContext, r: R, requestDelegate: ActorRef,
    pushChannel: ActorRef)

  trait RoutableToGorillaCenter extends RoutableByR

  case class NewSubscription(replyTo: ActorRef, rSubscription: RSubscription) extends RoutableToGorillaCenter {
    val r = rSubscription.r
  }

  trait PersistentEvent extends RoutableToGorillaCenter {
    def event: CollectionActorEvent
    val r = event.r
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

  trait GorillaLogEntry {
    def event: EventType

    def r: R

    def rev: Revision

    def modelVersion: ModelVersion

    def userInfo: UserInfo
  }

  case class GorillaLogCreatedEntry(r: R, obj: JsObject, rev: Revision,
      modelVersion: ModelVersion, userInfo: UserInfo) extends GorillaLogEntry {
    val event = "created"
  }

  case class GorillaLogUpdatedEntry(r: R, obj: JsObject, rev: Revision,
    modelVersion: ModelVersion, userInfo: UserInfo, ops: List[UpdateOp])
      extends GorillaLogEntry {
    val event = "updated"
  }

  case class GorillaLogDeletedEntry(r: R, rev: Revision, modelVersion: ModelVersion, userInfo: UserInfo) extends GorillaLogEntry {
    val event = "deleted"
  }

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
  /*
  * Represent implicit JSON Format for GorillaLogCreatedEntry
  */
  object GorillaLogDeletedEntry {
    implicit val fmt = Json.format[GorillaLogDeletedEntry]
  }

  implicit object GorillaLogEntryOrdering extends Ordering[GorillaLogEntry] {
    def compare(a: GorillaLogEntry, b: GorillaLogEntry) = a.rev compare b.rev
  }

}