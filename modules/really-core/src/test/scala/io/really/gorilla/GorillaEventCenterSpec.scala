/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import akka.actor.Props
import akka.testkit.{ TestProbe, TestActorRef }
import io.really.fixture.GorillaEventCenterFixture.GetState
import io.really.model.CollectionActor.CollectionActorEvent.{ Updated, Created }
import _root_.io.really.model.{ CollectionMetadata, Helpers }
import io.really._
import _root_.io.really.protocol.{ UpdateOp, UpdateCommand }
import play.api.libs.json.{ JsString, Json }
import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable

class GorillaEventCenterSpec extends BaseActorSpec {

  import EventLogs._

  implicit val session = globals.session

  val events: TableQuery[EventLogs] = TableQuery[EventLogs]
  val markers: TableQuery[EventLogMarkers] = TableQuery[EventLogMarkers]

  override def beforeAll() = {
    if (!MTable.getTables(EventLogs.tableName).list.isEmpty) {
      events.ddl.drop
      markers.ddl.drop
    }
    super.beforeAll()
  }

  override def afterAll() = {
    if (!MTable.getTables(EventLogs.tableName).list.isEmpty) {
      events.ddl.drop
      markers.ddl.drop
    }
    super.afterAll()
  }

  "Gorilla Event Center" should "should have the correct BucketID and R" in {
    val r = R / 'users / 123 / 'posts / 122
    val bucketID = Helpers.getBucketIDFromR(r)
    val db = Database.forURL(config.EventLogStorage.databaseUrl, driver = config.EventLogStorage.driver)
    implicit val session = db.createSession()
    val gorillaEventCenter = TestActorRef[GorillaEventCenter](Props(new GorillaEventCenter(globals)), bucketID).underlyingActor
    gorillaEventCenter.bucketID should be(bucketID)
    gorillaEventCenter.r should be(r.skeleton)
  }

  it should "should persist event in case of sending valid event" in {
    val probe = TestProbe()
    val r = R / 'users / 123
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val event = Created(r, obj, 1l, ctx, system.deadLetters, Result.CreateResult(r, obj))
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")
    (events.filter(_.r === r).length.run > 0) shouldBe true
    events.filter(_.r === r) foreach {
      element =>
        element shouldEqual EventLog("created", r, 1l, 1l, obj,
          ctx.auth, None)
    }
    markers.filter(_.r === r) foreach {
      element =>
        element shouldEqual (r, 1l)
    }

  }

  it should "should pesist event in case of sending update event with the entire object" in {
    val probe = TestProbe()
    val r = R / 'users / 124
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val ops = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
    val event = Updated(r, ops, 2l, 1l, ctx, system.deadLetters, Result.UpdateResult(r, 4))
    globals.gorillaEventCenter.tell(PersistentUpdatedEvent(event, obj), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")
    (events.filter(_.r === r).length.run > 0) shouldBe true
    events.filter(_.r === r) foreach {
      element =>
        element shouldEqual EventLog("updated", r, 2l, 1l, obj,
          ctx.auth, Some(ops))
    }
    (markers.filter(_.r === r).length.run > 0) shouldBe true
    markers.filter(_.r === r) foreach {
      element =>
        element shouldEqual (r, 2l)
    }
  }

  it should "ensure that we are not storing the same revision twice for the same object" in {
    val probe = TestProbe()
    val r = R / 'users / 124
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val ops = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
    val event = Updated(r, ops, 2l, 1l, ctx, system.deadLetters, Result.CreateResult(r, obj))
    globals.gorillaEventCenter.tell(PersistentUpdatedEvent(event, obj), probe.ref)
    globals.gorillaEventCenter.tell(PersistentUpdatedEvent(event, obj), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")
    (events.filter(_.r === r).length.run > 0) shouldBe true
    events.filter(_.r === r) foreach {
      element =>
        element shouldEqual EventLog("updated", r, 2l, 1l, obj,
          ctx.auth, Some(ops))
    }
    (markers.filter(_.r === r).length.run > 0) shouldBe true
    markers.filter(_.r === r) foreach {
      element =>
        element shouldEqual (r, 2l)
    }
  }
  it should "ensure that we are not storing the same r twice" in {
    val probe = TestProbe()
    val r = R / 'users / 123
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val event = Created(r, obj, 1l, ctx, system.deadLetters, Result.CreateResult(r, obj))
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event), probe.ref)
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")
    (events.filter(_.r === r).length.run == 1) shouldBe true
    events.filter(_.r === r) foreach {
      element =>
        element shouldEqual EventLog("created", r, 1l, 1l, obj,
          ctx.auth, None)
    }
    (markers.filter(_.r === r).length.run > 0) shouldBe true
    markers.filter(_.r === r) foreach {
      element =>
        element shouldEqual (r, 1l)
    }
  }
  it should "remove old model events when it receive ModelUpdated event" in {
    val probe = TestProbe()
    val r = R / 'users / globals.quickSand.nextId()
    val bucketID = Helpers.getBucketIDFromR(r)
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val event = Created(r, obj, 1l, ctx, system.deadLetters, Result.CreateResult(r, obj))
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(event), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    (events.filter(_.modelVersion === 1l).length.run > 0) shouldBe true

    val probe2 = TestProbe()
    val newModel = BaseActorSpec.userModel.copy(collectionMeta = CollectionMetadata(2))
    globals.gorillaEventCenter.tell(ModelUpdatedEvent(bucketID, newModel), probe2.ref)
    probe2.expectNoMsg()

    events.filter(_.modelVersion === 1l).length.run shouldBe 0
  }
}