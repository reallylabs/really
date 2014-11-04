/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import akka.actor.Props
import akka.testkit.{ TestProbe, TestActorRef }
import io.really.model.CollectionActor.Event.{ Updated, Created }
import io.really.model.{ CollectionMetadata, Helpers }
import io.really._
import _root_.io.really.protocol.{ UpdateOp, UpdateCommand }
import play.api.libs.json.{ JsString, Json }
import scala.slick.driver.H2Driver.simple._

class GorillaEventCenterSpec extends BaseActorSpec {

  implicit val session = globals.session
  val events: TableQuery[EventLogs] = TableQuery[EventLogs]

  "Gorilla Event Center" should "should have the correct BucketID and R" in {
    val r = R / 'users / 123 / 'posts / 122
    val bucketID = Helpers.getBucketIDFromR(r)
    val db = Database.forURL(config.EventLogStorage.databaseUrl, driver = config.EventLogStorage.driver)
    implicit val session = db.createSession()
    val gorillaEventCenter = TestActorRef[GorillaEventCenter](Props(new GorillaEventCenter(globals)), bucketID).underlyingActor
    gorillaEventCenter.bucketID should be(bucketID)
    gorillaEventCenter.r should be(r.skeleton)
  }

  it should "should return EventStored in case of sending valid event" in {
    val probe = TestProbe()
    val r = R / 'users / 123
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val event = Created(r, obj, 1l, ctx)
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event), probe.ref)
    probe.expectMsg(EventStored)

    events.filter(_.r === r.toString) foreach {
      element =>
        element shouldEqual EventLog("create", r.toString, 1l, 1l, Json.stringify(obj),
          Json.stringify(Json.toJson(ctx.auth)), None)
    }

  }

  it should "should return EventStored in case of sending update event with the entire object" in {
    val probe = TestProbe()
    val r = R / 'users / 124
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val ops = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
    val event = Updated(r, ops, 2l, 1l, ctx)
    globals.gorillaEventCenter.tell(PersistentUpdatedEvent(event, obj), probe.ref)
    probe.expectMsg(EventStored)

    events.filter(_.r === r.toString) foreach {
      element =>
        element shouldEqual EventLog("update", r.toString, 2l, 1l, Json.stringify(obj),
          Json.stringify(Json.toJson(ctx.auth)), Some(Json.stringify(Json.toJson(ops))))
    }
  }

  it should "remove old model events when it receive ModelUpdated event" in {
    val probe = TestProbe()
    val r = R / 'users / 125
    val bucketID = Helpers.getBucketIDFromR(r)
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val event = Created(r, obj, 1l, ctx)
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event), probe.ref)
    probe.expectMsg(EventStored)
    val newModel = BaseActorSpec.userModel.copy(collectionMeta = CollectionMetadata(2))
    globals.gorillaEventCenter.tell(ModelUpdatedEvent(bucketID, newModel), probe.ref)

    val r2 = R / 'users / 126
    val event2 = Created(r2, obj, 2l, ctx)
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event2), probe.ref)
    probe.expectMsg(EventStored)

    events.filter(_.ModelVersion > 1l) foreach {
      element =>
        element shouldEqual EventLog("create", r2.toString, 1l, 2l, Json.stringify(obj),
          Json.stringify(Json.toJson(ctx.auth)), None)
    }

  }
}