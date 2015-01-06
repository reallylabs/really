/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor.{ Actor, ActorRef, Props }
import akka.contrib.pattern.DistributedPubSubMediator.{ Subscribe, Publish }
import akka.persistence.{ Update => PersistenceUpdate }
import io.really.fixture.GorillaEventCenterFixture.GetState
import io.really.fixture.PersistentModelStoreFixture
import io.really.model.Model
import io.really.model.persistent.{ PersistentModelStore }
import akka.testkit.{ EventFilter, TestProbe, TestActorRef }
import com.typesafe.config.ConfigFactory
import _root_.io.really.Request.Create
import _root_.io.really.Result.{ UpdateResult, CreateResult }
import _root_.io.really.gorilla.Replayer.SnapshotResult.SnapshotObject
import _root_.io.really.protocol.SubscriptionFailure
import _root_.io.really.gorilla.SubscriptionManager.Unsubscribe
import _root_.io.really.model.CollectionActor.CollectionActorEvent.{ Updated, Created }
import _root_.io.really.protocol.{ UpdateBody, UpdateCommand, UpdateOp }
import io.really.Request.Update
import io.really._
import play.api.libs.json.{ JsString, Json }
import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable

import scala.slick.lifted.TableQuery

class ReplayerSpec(config: ReallyConfig) extends BaseActorSpecWithMongoDB(config) {

  class TestMaterializer extends Actor {
    def receive = Actor.emptyBehavior
  }

  override lazy val globals = new TestReallyGlobals(config, system) {

    override val materializerProps: Props = Props(new TestMaterializer())

  }

  def this() = this(new ReallyConfig(ConfigFactory.parseString("""
    really.core.akka.loggers = ["akka.testkit.TestEventListener"],
    really.core.akka.loglevel = WARNING
                                                                """).withFallback(TestConf.getConfig().getRawConfig)))

  implicit val session = globals.session
  val events: TableQuery[EventLogs] = TableQuery[EventLogs]
  val markers: TableQuery[EventLogMarkers] = TableQuery[EventLogMarkers]

  var modelRouterRef: ActorRef = _
  var modelPersistentActor: ActorRef = _
  val models: List[Model] = List(BaseActorSpec.userModel, BaseActorSpec.carModel,
    BaseActorSpec.companyModel, BaseActorSpec.authorModel, BaseActorSpec.postModel)

  override def beforeAll() = {
    if (!MTable.getTables(EventLogs.tableName).list.isEmpty) {
      events.ddl.drop
      markers.ddl.drop
    }
    super.beforeAll()
    modelRouterRef = globals.modelRegistry
    modelPersistentActor = globals.persistentModelStore

    modelPersistentActor ! PersistentModelStore.UpdateModels(models)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef ! PersistenceUpdate(await = true)
  }

  override def afterAll() = {
    if (!MTable.getTables(EventLogs.tableName).list.isEmpty) {
      events.ddl.drop
      markers.ddl.drop
    }
    super.afterAll()
  }

  "Replayer" should "start with accurate state" in {
    val objectSubActor = TestProbe()
    val requestDelegate = TestProbe().ref
    val pushChannel = TestProbe().ref
    val rev = 23
    val r: R = R / 'users / 123
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
    val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(rev))))
    replayer.underlyingActor.r should be(r)
    replayer.underlyingActor.min should be(rev)
    replayer.underlyingActor.replayerId should be(s"Replayer ${rSub.pushChannel.path}$$${rSub.r}")

    //Test handling unexpected message
    EventFilter.warning(message = s"${replayer.underlyingActor.replayerId} has received an unexpected message:" +
      s" Unexpected!", occurrences = 1) intercept {
      replayer ! "Unexpected!"
    }
    //Test handling unsubscribe message
    val watchDog = TestProbe()
    watchDog.watch(replayer)
    replayer ! Unsubscribe
    watchDog.expectTerminated(replayer)
    //Test handling Object Subscription Actor death
    val objectSubActor2 = TestActorRef[ObjectSubscriber](Props(new ObjectSubscriber(rSub, globals)))
    val replayer2 = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor2, rSub, Some(rev))))
    watchDog.watch(replayer2)
    system.stop(objectSubActor2)
    watchDog.expectTerminated(replayer2)
  }

  it should "serve the PUSH updates directly if the subscription revision equals the max marker in Gorilla" in {
    val objectSubActor = TestProbe()
    val requestDelegate = TestProbe().ref
    val pushChannel = TestProbe().ref
    val rev: Revision = 2L
    val r: R = R / 'users / 124
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
    val probe = TestProbe()
    val obj = Json.obj("name" -> "Sara", "age" -> 20)
    val createdEvent = Created(r, obj, 1l, ctx)
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(createdEvent), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    val ops1 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
    val updatedEvent1 = Updated(r, ops1, 2L, 1l, ctx)
    globals.gorillaEventCenter.tell(PersistentUpdatedEvent(updatedEvent1, obj), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(rev))))
    globals.mediator ! Subscribe(rSub.r.toString, replayer)

    val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
    val updatedEvent2 = Updated(r, ops2, 3L, 1l, ctx)
    val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 3L, "name" -> "ahmed", "age" -> 20)
    globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent2, fullObj2))

    val ops3 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
    val updatedEvent3 = Updated(r, ops3, 4L, 1l, ctx)
    val fullObj3 = Json.obj("_r" -> r.toString, "_rev" -> 4L, "name" -> "hamada", "age" -> 20)
    globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent3, fullObj3))

    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj2, 3L, 1L, updatedEvent2.context.auth, ops2))
    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj3, 4L, 1L, updatedEvent3.context.auth, ops3))

    //Test handling unexpected message
    EventFilter.warning(message = s"${replayer.underlyingActor.replayerId} has received an unexpected message:" +
      s" Unexpected!", occurrences = 1) intercept {
      replayer ! "Unexpected!"
    }
    //Test handling unsubscribe message
    val watchDog = TestProbe()
    watchDog.watch(replayer)
    replayer ! Unsubscribe
    watchDog.expectTerminated(replayer)
  }

  it should "wait the correct revision pushed before serving the PUSH updates if the subscription revision is" +
    " greater than the max marker in Gorilla" in {
      val objectSubActor = TestProbe()
      val requestDelegate = TestProbe().ref
      val pushChannel = TestProbe().ref
      val rev: Revision = 3L
      val r: R = R / 'users / 125
      val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
      val probe = TestProbe()
      val obj = Json.obj("name" -> "Sara", "age" -> 20)
      val createdEvent = Created(r, obj, 1l, ctx)
      globals.gorillaEventCenter.tell(PersistentCreatedEvent(createdEvent), probe.ref)
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val ops1 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
      val updatedEvent1 = Updated(r, ops1, 2L, 1l, ctx)
      globals.gorillaEventCenter.tell(PersistentUpdatedEvent(updatedEvent1, obj), probe.ref)
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(rev - 1))))
      globals.mediator ! Subscribe(rSub.r.toString, replayer)

      val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
      val updatedEvent2 = Updated(r, ops2, 3L, 1l, ctx)
      val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 3L, "name" -> "ahmed", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent2, fullObj2))

      val ops3 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
      val updatedEvent3 = Updated(r, ops3, 4L, 1l, ctx)
      val fullObj3 = Json.obj("_r" -> r.toString, "_rev" -> 4L, "name" -> "hamada", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent3, fullObj3))

      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj3, 4L, 1L, updatedEvent3.context.auth, ops3))

      //Test handling Object Subscription Actor death
      val watchDog = TestProbe()
      watchDog.watch(replayer)
      system.stop(objectSubActor.ref)
      watchDog.expectTerminated(replayer)
    }

  it should "fail to wait the correct revision if the subscription revision is greater than the max marker in Gorilla" +
    " but exceeds the advancing limit" in {
      val objectSubActor = TestProbe()
      val requestDelegate = TestProbe().ref
      val pushChannel = TestProbe().ref
      val rev: Revision = 33L
      val r: R = R / 'users / 126
      val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
      val probe = TestProbe()
      val obj = Json.obj("name" -> "Sara", "age" -> 20)
      val createdEvent = Created(r, obj, 1l, ctx)
      globals.gorillaEventCenter.tell(PersistentCreatedEvent(createdEvent), probe.ref)
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val ops1 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
      val updatedEvent1 = Updated(r, ops1, 2L, 1l, ctx)
      globals.gorillaEventCenter.tell(PersistentUpdatedEvent(updatedEvent1, obj), probe.ref)
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(2))))
      globals.mediator ! Subscribe(rSub.r.toString, replayer)
      probe.watch(replayer)

      val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
      val updatedEvent2 = Updated(r, ops2, 3L, 1l, ctx)
      val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 3L, "name" -> "ahmed", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent2, fullObj2))

      val ops3 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
      val updatedEvent3 = Updated(r, ops3, 4L, 1l, ctx)
      val fullObj3 = Json.obj("_r" -> r.toString, "_rev" -> 4L, "name" -> "hamada", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent3, fullObj3))

      objectSubActor.expectMsg(SubscriptionFailure(r, 500, "Subscription revision advances the current known revision" +
        " with more than the advancing limit"))
      probe.expectTerminated(replayer)

    }

  it should "shutdown while waiting the correct revision if the first pushed update has a higher rev that makes a" +
    " hole" in {
      val objectSubActor = TestProbe()
      val requestDelegate = TestProbe().ref
      val pushChannel = TestProbe().ref
      val rev: Revision = 10L
      val r: R = R / 'users / 127
      val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
      val probe = TestProbe()

      val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(5))))
      probe.watch(replayer)
      globals.mediator ! Subscribe(rSub.r.toString, replayer)

      val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
      val updatedEvent2 = Updated(r, ops2, 12L, 1l, ctx)
      val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 12L, "name" -> "ahmed", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent2, fullObj2))

      objectSubActor.expectMsg(SubscriptionFailure(r, 500, "Inconsistent push updates revision"))
      probe.expectTerminated(replayer)
    }

  it should "push a snapshot first if the subscription rev is lower than the max marker but found no logs in DB" in {
    val objectSubActor = TestProbe()
    val requestDelegate = TestProbe().ref
    val pushChannel = TestProbe().ref
    val rev: Revision = 2L
    val r: R = R / 'users / 128
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
    val probe = TestProbe()

    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
    globals.collectionActor.tell(Update(ctx, r, 1L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 2L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 3L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 4L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 5L, body), probe.ref)
    probe.receiveN(4)
    val em = probe.expectMsgType[UpdateResult]
    em.rev shouldEqual 6L

    val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(5L))))
    globals.mediator ! Subscribe(rSub.r.toString, replayer)

    val snapshot = Json.obj("_r" -> r.toString, "_rev" -> 6L, "name" -> "Amal", "age" -> 27)
    objectSubActor.expectMsg(SnapshotObject(snapshot))

    //Test handling unexpected message
    EventFilter.warning(message = s"${replayer.underlyingActor.replayerId} has received an unexpected message:" +
      s" Unexpected!", occurrences = 1) intercept {
      replayer ! "Unexpected!"
    }

    val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
    val updatedEvent2 = Updated(r, ops2, 6L, 1l, ctx)
    val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 6L, "name" -> "ahmed", "age" -> 20)
    globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent2, fullObj2))

    val ops3 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
    val updatedEvent3 = Updated(r, ops3, 7L, 1l, ctx)
    val fullObj3 = Json.obj("_r" -> r.toString, "_rev" -> 7L, "name" -> "hamada", "age" -> 20)
    globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent3, fullObj3))

    //Test handling unsubscribe message
    val watchDog = TestProbe()
    watchDog.watch(replayer)
    replayer ! Unsubscribe

    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj2, 6L, 1L, updatedEvent2.context.auth, ops2))
    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj3, 7L, 1L, updatedEvent3.context.auth, ops3))
    watchDog.expectTerminated(replayer)
  }

  it should "push a snapshot first if the subscription rev is lower than the max marker but found a hole in the" +
    " events log and then wait for Push updates" in {
      val objectSubActor = TestProbe()
      val requestDelegate = TestProbe().ref
      val pushChannel = TestProbe().ref
      val rev: Revision = 2L
      val r: R = R / 'users / 129
      val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
      val probe = TestProbe()

      globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
      probe.expectMsgType[CreateResult]
      val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
      globals.collectionActor.tell(Update(ctx, r, 1L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 2L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 3L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 4L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 5L, body), probe.ref)
      probe.receiveN(4)
      val em = probe.expectMsgType[UpdateResult]
      em.rev shouldEqual 6L

      val ops1 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
      val updatedEvent1 = Updated(r, ops1, 4L, 1l, ctx)
      globals.gorillaEventCenter.tell(
        PersistentUpdatedEvent(updatedEvent1, Json.obj("name" -> "amal", "age" -> 20)),
        probe.ref
      )
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("koko")))
      val updatedEvent2 = Updated(r, ops2, 5L, 1l, ctx)
      globals.gorillaEventCenter.tell(
        PersistentUpdatedEvent(updatedEvent2, Json.obj("name" -> "koko", "age" -> 20)),
        probe.ref
      )
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(4L))))
      globals.mediator ! Subscribe(rSub.r.toString, replayer)

      val snapshot = Json.obj("_r" -> r.toString, "_rev" -> 6L, "name" -> "Amal", "age" -> 27)
      objectSubActor.expectMsg(SnapshotObject(snapshot))

      val ops4 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
      val updatedEvent4 = Updated(r, ops4, 7L, 1l, ctx)
      val fullObj4 = Json.obj("_r" -> r.toString, "_rev" -> 7L, "name" -> "ahmed", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent4, fullObj4))

      val ops5 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
      val updatedEvent5 = Updated(r, ops5, 8L, 1l, ctx)
      val fullObj5 = Json.obj("_r" -> r.toString, "_rev" -> 8L, "name" -> "hamada", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent5, fullObj5))

      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj4, 7L, 1L, updatedEvent4.context.auth, ops4))
      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj5, 8L, 1L, updatedEvent5.context.auth, ops5))
    }

  it should "fail if it had to fetch a snapshot and the object was not found" in {
    val objectSubActor = TestProbe()
    val requestDelegate = TestProbe().ref
    val pushChannel = TestProbe().ref
    val rev: Revision = 2L
    val r: R = R / 'users / 130
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
    val probe = TestProbe()

    val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, Some(5L))))
    probe.watch(replayer)
    globals.mediator ! Subscribe(rSub.r.toString, replayer)

    objectSubActor.expectMsg(SubscriptionFailure(r, 500, "Failed to retrieve a snapshot for that object"))
    probe.expectTerminated(replayer)
  }

  it should "get logs if matches the rev first then switch to serve push updates" in {
    val objectSubActor = TestProbe()
    val requestDelegate = TestProbe().ref
    val pushChannel = TestProbe().ref
    val rev: Revision = 1L
    val r: R = R / 'users / 131
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
    val probe = TestProbe()
    val fullObj1 = Json.obj("_r" -> r.toString, "_rev" -> 1L, "name" -> "Sara", "age" -> 20)
    val createdEvent = Created(r, fullObj1, 1l, ctx)
    globals.gorillaEventCenter.tell(PersistentCreatedEvent(createdEvent), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
    val updatedEvent2 = Updated(r, ops2, 2L, 1l, ctx)
    val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 2L, "name" -> "amal", "age" -> 20)
    globals.gorillaEventCenter.tell(PersistentUpdatedEvent(updatedEvent2, fullObj2), probe.ref)
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, None)))
    globals.mediator ! Subscribe(rSub.r.toString, replayer)

    val ops3 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
    val updatedEvent3 = Updated(r, ops3, 3L, 1l, ctx)
    val fullObj3 = Json.obj("_r" -> r.toString, "_rev" -> 3L, "name" -> "ahmed", "age" -> 20)
    globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent3, fullObj3))

    val ops4 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
    val updatedEvent4 = Updated(r, ops4, 4L, 1l, ctx)
    val fullObj4 = Json.obj("_r" -> r.toString, "_rev" -> 4L, "name" -> "hamada", "age" -> 20)
    globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent4, fullObj4))

    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj2, 2L, 1L, updatedEvent2.context.auth, ops2))
    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj3, 3L, 1L, updatedEvent3.context.auth, ops3))
    objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj4, 4L, 1L, updatedEvent4.context.auth, ops4))

    //Test handling unexpected message
    EventFilter.warning(message = s"${replayer.underlyingActor.replayerId} has received an unexpected message:" +
      s" Unexpected!", occurrences = 1) intercept {
      replayer ! "Unexpected!"
    }
    //Test handling unsubscribe message
    val watchDog = TestProbe()
    watchDog.watch(replayer)
    replayer ! Unsubscribe
    watchDog.expectTerminated(replayer)
  }

  it should "get a snapshot if there was no logs in the events log and the snapshot version is higher than the" +
    " subscription revision" in {
      val objectSubActor = TestProbe()
      val requestDelegate = TestProbe().ref
      val pushChannel = TestProbe().ref
      val rev: Revision = 2L
      val r: R = R / 'users / 132
      val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
      val probe = TestProbe()

      globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
      probe.expectMsgType[CreateResult]
      val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
      globals.collectionActor.tell(Update(ctx, r, 1L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 2L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 3L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 4L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 5L, body), probe.ref)
      probe.receiveN(4)
      val em = probe.expectMsgType[UpdateResult]
      em.rev shouldEqual 6L

      val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, None)))
      globals.mediator ! Subscribe(rSub.r.toString, replayer)

      val snapshot = Json.obj("_r" -> r.toString, "_rev" -> 6L, "name" -> "Amal", "age" -> 27)
      objectSubActor.expectMsg(SnapshotObject(snapshot))

      val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
      val updatedEvent2 = Updated(r, ops2, 6L, 1l, ctx)
      val fullObj2 = Json.obj("_r" -> r.toString, "_rev" -> 6L, "name" -> "ahmed", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent2, fullObj2))

      val ops3 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
      val updatedEvent3 = Updated(r, ops3, 7L, 1l, ctx)
      val fullObj3 = Json.obj("_r" -> r.toString, "_rev" -> 7L, "name" -> "hamada", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent3, fullObj3))

      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj2, 6L, 1L, updatedEvent2.context.auth, ops2))
      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj3, 7L, 1L, updatedEvent3.context.auth, ops3))
    }

  it should "get a snapshot if there were some logs but with a hole and the snapshot revision is higher than the" +
    " subscription revision" in {
      val objectSubActor = TestProbe()
      val requestDelegate = TestProbe().ref
      val pushChannel = TestProbe().ref
      val rev: Revision = 2L
      val r: R = R / 'users / 99897
      val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
      val probe = TestProbe()

      globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
      probe.expectMsgType[CreateResult]
      val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
      globals.collectionActor.tell(Update(ctx, r, 1L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 2L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 3L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 4L, body), probe.ref)
      globals.collectionActor.tell(Update(ctx, r, 5L, body), probe.ref)
      probe.receiveN(4)
      val em = probe.expectMsgType[UpdateResult]
      em.rev shouldEqual 6L

      val ops1 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
      val updatedEvent1 = Updated(r, ops1, 4L, 1l, ctx)
      globals.gorillaEventCenter.tell(
        PersistentUpdatedEvent(updatedEvent1, Json.obj("name" -> "amal", "age" -> 20)),
        probe.ref
      )
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("koko")))
      val updatedEvent2 = Updated(r, ops2, 5L, 1l, ctx)
      globals.gorillaEventCenter.tell(
        PersistentUpdatedEvent(updatedEvent2, Json.obj("name" -> "koko", "age" -> 20)),
        probe.ref
      )
      globals.gorillaEventCenter.tell(GetState(r), probe.ref)
      probe.expectMsg("done")

      val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, None)))
      globals.mediator ! Subscribe(rSub.r.toString, replayer)

      val snapshot = Json.obj("_r" -> r.toString, "_rev" -> 6L, "name" -> "Amal", "age" -> 27)
      objectSubActor.expectMsg(SnapshotObject(snapshot))

      val ops4 = List(UpdateOp(UpdateCommand.Set, "name", JsString("ahmed")))
      val updatedEvent4 = Updated(r, ops4, 7L, 1l, ctx)
      val fullObj4 = Json.obj("_r" -> r.toString, "_rev" -> 7L, "name" -> "ahmed", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent4, fullObj4))

      val ops5 = List(UpdateOp(UpdateCommand.Set, "name", JsString("hamada")))
      val updatedEvent5 = Updated(r, ops5, 8L, 1l, ctx)
      val fullObj5 = Json.obj("_r" -> r.toString, "_rev" -> 8L, "name" -> "hamada", "age" -> 20)
      globals.mediator ! Publish(r.toString, PersistentUpdatedEvent(updatedEvent5, fullObj5))

      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj4, 7L, 1L, updatedEvent4.context.auth, ops4))
      objectSubActor.expectMsg(GorillaLogUpdatedEntry(r, fullObj5, 8L, 1L, updatedEvent5.context.auth, ops5))
    }

  it should "fail if the snapshot revision was lower than the subscription revision by more than the gap margin" in {
    val objectSubActor = TestProbe()
    val requestDelegate = TestProbe().ref
    val pushChannel = TestProbe().ref
    val rev: Revision = 69L
    val r: R = R / 'users / 134
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate, pushChannel)
    val probe = TestProbe()

    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
    globals.collectionActor.tell(Update(ctx, r, 1L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 2L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 3L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 4L, body), probe.ref)
    globals.collectionActor.tell(Update(ctx, r, 5L, body), probe.ref)
    probe.receiveN(4)
    val em = probe.expectMsgType[UpdateResult]
    em.rev shouldEqual 6L

    val ops1 = List(UpdateOp(UpdateCommand.Set, "name", JsString("amal")))
    val updatedEvent1 = Updated(r, ops1, 4L, 1l, ctx)
    globals.gorillaEventCenter.tell(
      PersistentUpdatedEvent(updatedEvent1, Json.obj("name" -> "amal", "age" -> 20)),
      probe.ref
    )
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    val ops2 = List(UpdateOp(UpdateCommand.Set, "name", JsString("koko")))
    val updatedEvent2 = Updated(r, ops2, 5L, 1l, ctx)
    globals.gorillaEventCenter.tell(
      PersistentUpdatedEvent(updatedEvent2, Json.obj("name" -> "koko", "age" -> 20)),
      probe.ref
    )
    globals.gorillaEventCenter.tell(GetState(r), probe.ref)
    probe.expectMsg("done")

    val replayer = TestActorRef[Replayer](Props(new Replayer(globals, objectSubActor.ref, rSub, None)))
    probe.watch(replayer)
    globals.mediator ! Subscribe(rSub.r.toString, replayer)

    objectSubActor.expectMsg(SubscriptionFailure(r, 500, "Subscription revision advances the current known revision with more" +
      " than the advancing limit"))
    probe.expectTerminated(replayer)
  }

}
