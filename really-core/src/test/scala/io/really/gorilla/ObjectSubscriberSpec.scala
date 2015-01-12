/*
* Copyright (C) 2014-2015 Really Inc. <http://really.io>
*/

package io.really.gorilla

import akka.actor.Props
import akka.testkit.{ EventFilter, TestProbe, TestActorRef }
import _root_.io.really._
import _root_.io.really.gorilla.SubscriptionManager.{ UpdateSubscriptionFields, Unsubscribe }
import _root_.io.really.protocol.ProtocolFormats.PushMessageWrites.{ Updated, Deleted }
import _root_.io.really.protocol.{ UpdateOp, SubscriptionFailure, UpdateCommand, FieldUpdatedOp }
import _root_.io.really.protocol.SubscriptionFailure.SubscriptionFailureWrites
import _root_.io.really.gorilla.GorillaEventCenter.ReplayerSubscribed
import _root_.io.really.fixture.PersistentModelStoreFixture
import _root_.io.really.model.persistent.ModelRegistry.RequestModel
import _root_.io.really.model.persistent.ModelRegistry.ModelResult
import _root_.io.really.model.persistent.PersistentModelStore
import _root_.io.really.model.persistent.ModelRegistry.ModelOperation.{ ModelUpdated, ModelDeleted }
import _root_.io.really.gorilla.mock.TestObjectSubscriber
import _root_.io.really.gorilla.mock.TestObjectSubscriber.{ Ping, GetFieldList, Pong }
import akka.persistence.{ Update => PersistenceUpdate }
import com.typesafe.config.ConfigFactory
import _root_.io.really.model._
import play.api.libs.json._

class ObjectSubscriberSpec(config: ReallyConfig) extends BaseActorSpecWithMongoDB(config) {
  def this() = this(new ReallyConfig(ConfigFactory.parseString("""
    really.core.akka.loggers = ["akka.testkit.TestEventListener"],
    really.core.akka.loglevel = WARNING
    really.core.gorilla.wait-for-replayer = 1ms
                                                                """).withFallback(TestConf.getConfig().getRawConfig)))

  override lazy val globals = new TestReallyGlobals(config, system) {
    override def objectSubscriberProps(rSubscription: RSubscription): Props =
      Props(classOf[TestObjectSubscriber], rSubscription, this)
  }

  val userInfo = UserInfo(AuthProvider.Anonymous, "34567890", Some(R("/_anonymous/1234567")), Json.obj())
  implicit val session = globals.session

  val friendOnGetJs: JsScript =
    """
      |hide("lName");
    """.stripMargin

  val friendModel = Model(
    R / 'friend,
    CollectionMetadata(23),
    Map(
      "fName" -> ValueField("fName", DataType.RString, None, None, true),
      "lName" -> ValueField("lName", DataType.RString, None, None, true),
      "age" -> ValueField("age", DataType.RLong, None, None, true)
    ),
    JsHooks(
      None,
      Some(friendOnGetJs),
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List.empty
  )

  val brandOnGetJs: JsScript =
    """
      |cancel(23, "Unexpected Error");
    """.stripMargin

  val brandModel = Model(
    R / 'brand,
    CollectionMetadata(23),
    Map(
      "name" -> ValueField("name", DataType.RString, None, None, true),
      "since" -> ValueField("since", DataType.RLong, None, None, true)
    ),
    JsHooks(
      None,
      Some(brandOnGetJs),
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List.empty
  )

  val models: List[Model] = List(BaseActorSpec.userModel, BaseActorSpec.carModel,
    BaseActorSpec.companyModel, BaseActorSpec.authorModel, BaseActorSpec.postModel, friendModel, brandModel)

  override def beforeAll() = {
    super.beforeAll()
    globals.persistentModelStore ! PersistentModelStore.UpdateModels(models)
    globals.persistentModelStore ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    globals.modelRegistry ! PersistenceUpdate(await = true)
    globals.modelRegistry ! RequestModel.GetModel(BaseActorSpec.userModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.userModel, List.empty))
    globals.modelRegistry ! RequestModel.GetModel(brandModel.r, self)
    expectMsg(ModelResult.ModelObject(brandModel, List.empty))
  }

  "Object Subscriber" should "Initialized Successfully" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub1 = RSubscription(ctx, r, Some(Set("name")), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor1 = TestActorRef[TestObjectSubscriber](globals.objectSubscriberProps(rSub1))
    objectSubscriberActor1.underlyingActor.fields shouldEqual Set("name")
    objectSubscriberActor1.underlyingActor.r shouldEqual r
    objectSubscriberActor1.underlyingActor.logTag shouldEqual s"ObjectSubscriber ${pushChannel.ref.path}$$$r"

    val rSub2 = RSubscription(ctx, r, Some(Set.empty), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor2 = system.actorOf(globals.objectSubscriberProps(rSub2))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor2, rSub2, Some(rev))))
    objectSubscriberActor2 ! ReplayerSubscribed(replayer)
    objectSubscriberActor2.tell(Ping, testProbe.ref)
    testProbe.expectMsg(Pong)
    objectSubscriberActor2.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("name", "age"))
  }

  it should "handle Unsubscribe successfully during starter receiver and self termination" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, Some(Set("name")), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    deathProbe.watch(objectSubscriberActor)
    objectSubscriberActor ! SubscriptionManager.Unsubscribe
    requestDelegate.expectMsg(SubscriptionManager.Unsubscribe)
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "handle Unsubscribe during withModel and self termination" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 602
    val rSub = RSubscription(ctx, r, Some(Set.empty), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    deathProbe.watch(objectSubscriberActor)
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    objectSubscriberActor.tell(Ping, testProbe.ref)
    testProbe.expectMsg(Pong)
    objectSubscriberActor.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("name", "age"))
    objectSubscriberActor ! Unsubscribe
    requestDelegate.expectMsg(Unsubscribe)
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "update Internal field List Successfully" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, Some(Set("name")), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    objectSubscriberActor.tell(Ping, testProbe.ref)
    testProbe.expectMsg(Pong)
    objectSubscriberActor ! UpdateSubscriptionFields(Set("age"))
    objectSubscriberActor.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("name", "age"))
  }

  it should "pass delete updates to push channel actor correctly and then terminates" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, Some(Set("name")), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    objectSubscriberActor.tell(Ping, testProbe.ref)
    testProbe.expectMsg(Pong)
    objectSubscriberActor.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("name"))
    objectSubscriberActor ! GorillaLogDeletedEntry(r, rev, 1l, userInfo)
    pushChannel.expectMsg(Deleted.toJson(r, userInfo))
    deathProbe.watch(objectSubscriberActor)
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "for empty list subscription change the internal state from empty list to all model fields" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriber = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriber, rSub, Some(rev))))
    objectSubscriber ! ReplayerSubscribed(replayer)
    objectSubscriber.tell(Ping, testProbe.ref)
    testProbe.expectMsg(Pong)
    objectSubscriber.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("name", "age"))
  }

  it should "fail with internal server error if the model version was inconsistent" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    deathProbe.watch(objectSubscriberActor)
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    EventFilter.error(occurrences = 1, message = s"ObjectSubscriber ${pushChannel.ref.path}$$$r is going to die since" +
      s" the subscription failed because of: Model Version inconsistency\n error code: 502") intercept {
      objectSubscriberActor ! GorillaLogUpdatedEntry(rSub.r, Json.obj(), 2L, 1L, ctx.auth, List())
    }
  }

  it should "filter the hidden fields from the an empty list subscription and sent the rest of model fields" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r = R / 'friend / 1
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val friendSub = rSub.copy(r = r)
    val objectSubscriber = system.actorOf(globals.objectSubscriberProps(friendSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriber, friendSub, Some(rev))))
    objectSubscriber ! ReplayerSubscribed(replayer)
    val friendObject = Json.obj("_r" -> r, "_rev" -> 1L, "fName" -> "Ahmed", "age" -> 23, "lName" -> "Refaey")
    val createdEvent = GorillaLogCreatedEntry(r, friendObject, 1L, 23L, ctx.auth)
    objectSubscriber ! GorillaLogUpdatedEntry(rSub.r, Json.obj(), 1L, 23L, ctx.auth, List.empty)
    pushChannel.expectMsg(Updated.toJson(r, 1L, List.empty, ctx.auth))
    objectSubscriber.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("fName", "lName", "age"))
  }

  it should "filter the hidden fields from the the subscription list and sent the rest of the subscription list" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'friend / 2
    val friendSub = RSubscription(ctx, r, Some(Set("fName", "lName")), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriber = system.actorOf(globals.objectSubscriberProps(friendSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriber, friendSub, Some(rev))))
    objectSubscriber ! ReplayerSubscribed(replayer)
    objectSubscriber.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("fName", "lName"))
    val updates: List[UpdateOp] = List(
      UpdateOp(UpdateCommand.Set, "fName", JsString("Ahmed")),
      UpdateOp(UpdateCommand.Set, "lName", JsString("Refaey"))
    )
    objectSubscriber ! GorillaLogUpdatedEntry(friendSub.r, Json.obj(), 1L, 23L, ctx.auth, updates)
    pushChannel.expectMsg(Updated.toJson(r, 1L, List(FieldUpdatedOp("fName", UpdateCommand.Set, Some(JsString("Ahmed")))), ctx.auth))
  }

  ignore should "pass nothing if the model.executeOnGet evaluated to Terminated" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r = R / 'brand / 1
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val brandSub = rSub.copy(r = r, fields = Some(Set("name", "since")))
    val objectSubscriber = system.actorOf(globals.objectSubscriberProps(brandSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriber, brandSub, Some(rev))))
    objectSubscriber ! ReplayerSubscribed(replayer)
    objectSubscriber.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("name", "since"))
    val brandObject = Json.obj("_r" -> r, "_rev" -> 1L, "name" -> "QuickSilver", "since" -> 1969)
    val createdEvent = GorillaLogCreatedEntry(r, brandObject, 1L, 23L, ctx.auth)
    objectSubscriber ! createdEvent
    pushChannel.expectNoMsg()
  }

  it should "in case of subscription failure, log and acknowledge the delegate and then stop" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    deathProbe.watch(objectSubscriberActor)
    EventFilter.error(occurrences = 1, message = s"ObjectSubscriber ${rSub.pushChannel.path}$$${r} is going to die since the subscription failed because of: Internal Server Error\n error code: 401") intercept {
      objectSubscriberActor ! SubscriptionFailure(r, 401, "Test Error Reason")
    }
    pushChannel.expectMsg(SubscriptionFailureWrites.writes(SubscriptionFailure(r, 401, "Internal Server Error")))
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "handle associated replayer termination" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    deathProbe.watch(objectSubscriberActor)
    EventFilter.error(occurrences = 1, message = s"ObjectSubscriber ${rSub.pushChannel.path}$$${r} is going to die since the subscription failed because of: Associated replayer stopped\n error code: 505") intercept {
      system.stop(replayer)
    }
    pushChannel.expectMsg(SubscriptionFailureWrites.writes(SubscriptionFailure(r, 505, "Internal Server Error")))
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "handle ModelUpdated correctly" in {
    val testProbe = TestProbe()
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'friend / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    val updates1: List[UpdateOp] = List(
      UpdateOp(UpdateCommand.Set, "age", JsNumber(23)),
      UpdateOp(UpdateCommand.Set, "fName", JsString("Ahmed")),
      UpdateOp(UpdateCommand.Set, "lName", JsString("Refaey"))
    )
    objectSubscriberActor ! GorillaLogUpdatedEntry(rSub.r, Json.obj(), 1L, 23L, ctx.auth, updates1)
    pushChannel.expectMsg(Updated.toJson(r, 1L, List(
      FieldUpdatedOp("fName", UpdateCommand.Set, Some(JsString("Ahmed"))),
      FieldUpdatedOp("age", UpdateCommand.Set, Some(JsNumber(23)))
    ), ctx.auth))
    objectSubscriberActor.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("fName", "lName", "age"))
    val friendOnGetJs: JsScript =
      """
        |hide("fName");
      """.stripMargin
    val newFriendModel = friendModel.copy(jsHooks = JsHooks(
      None,
      Some(friendOnGetJs),
      None,
      None,
      None,
      None,
      None
    ))
    objectSubscriberActor ! ModelUpdated(rSub.r.skeleton, newFriendModel, List())
    val updates2: List[UpdateOp] = List(
      UpdateOp(UpdateCommand.Set, "age", JsNumber(29)),
      UpdateOp(UpdateCommand.Set, "fName", JsString("Neo")),
      UpdateOp(UpdateCommand.Set, "lName", JsString("Anderson"))
    )
    objectSubscriberActor ! GorillaLogUpdatedEntry(rSub.r, Json.obj(), 1L, 23L, ctx.auth, updates2)
    pushChannel.expectMsg(Updated.toJson(r, 1L, List(
      FieldUpdatedOp("age", UpdateCommand.Set, Some(JsNumber(29))),
      FieldUpdatedOp("lName", UpdateCommand.Set, Some(JsString("Anderson")))
    ), ctx.auth))
    objectSubscriberActor.tell(GetFieldList, testProbe.ref)
    testProbe.expectMsg(Set("fName", "lName", "age"))
  }

  it should "handle ModelDeleted, send subscription failed and terminates" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    deathProbe.watch(objectSubscriberActor)
    EventFilter.error(occurrences = 1, message = s"ObjectSubscriber ${rSub.pushChannel.path}$$${r} is going to die" +
      s" since the subscription failed because of: received a DeletedModel message for: $r\n error code: 501") intercept {
      objectSubscriberActor ! ModelDeleted(rSub.r.skeleton)
    }
    pushChannel.expectMsg(SubscriptionFailureWrites.writes(SubscriptionFailure(r, 501, "Internal Server Error")))
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "handle if the pushed update model version is not equal to the state version" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriberActor, rSub, Some(rev))))
    objectSubscriberActor ! ReplayerSubscribed(replayer)
    deathProbe.watch(objectSubscriberActor)
    EventFilter.error(occurrences = 1, message = s"ObjectSubscriber ${rSub.pushChannel.path}$$${r} is going to die since the subscription failed because of: Model Version inconsistency\n error code: 502") intercept {
      objectSubscriberActor ! GorillaLogUpdatedEntry(rSub.r, Json.obj(), 2L, 1L, ctx.auth, List())
    }
    pushChannel.expectMsg(SubscriptionFailureWrites.writes(SubscriptionFailure(r, 502, "Internal Server Error")))
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "suicide if the associated Replayer did not send a ReplayerSubscribed for a configurable time" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val deathProbe = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, None, rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriberActor = system.actorOf(globals.objectSubscriberProps(rSub))
    deathProbe.watch(objectSubscriberActor)
    objectSubscriberActor ! "To stash message 1"
    objectSubscriberActor ! "To stash message 2"
    objectSubscriberActor ! "To stash message 3"
    deathProbe.expectTerminated(objectSubscriberActor)
  }

  it should "fail the subscription not a field in the subscription body relates to the object's model" in {
    val requestDelegate = TestProbe()
    val pushChannel = TestProbe()
    val rev: Revision = 1L
    val r: R = R / 'users / 601
    val rSub = RSubscription(ctx, r, Some(Set("notAField1", "notAField2")), rev, requestDelegate.ref, pushChannel.ref)
    val objectSubscriber = system.actorOf(globals.objectSubscriberProps(rSub))
    val replayer = system.actorOf(Props(new Replayer(globals, objectSubscriber, rSub, Some(rev))))
    objectSubscriber ! ReplayerSubscribed(replayer)
    val failureMessage = SubscriptionFailureWrites.writes(SubscriptionFailure(r, 506, "Internal Server Error"))
    pushChannel.expectMsg(failureMessage)
  }

}
