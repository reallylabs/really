/*
* Copyright (C) 2014-2015 Really Inc. <http://really.io>
*/

package io.really.gorilla

import akka.actor.{ ActorRef, Props }
import akka.testkit.{ TestProbe, TestActorRef }
import akka.persistence.{ Update => PersistenceUpdate }
import io.really._
import _root_.io.really.model.{ Model, FieldKey }
import _root_.io.really.model.persistent.ModelRegistry.{ RequestModel, ModelResult }
import _root_.io.really.model.persistent.PersistentModelStore
import _root_.io.really.fixture.PersistentModelStoreFixture
import _root_.io.really.gorilla.mock.TestObjectSubscriber
import _root_.io.really.gorilla.mock.TestObjectSubscriber._
import _root_.io.really.protocol.{ SubscriptionBody, SubscriptionOp }
import _root_.io.really.gorilla.SubscribeAggregator.Subscribed
import slick.driver.H2Driver.simple.Session

class SubscriptionManagerSpec extends BaseActorSpecWithMongoDB {

  override lazy val globals = new TestReallyGlobals(config, system) {

    override def objectSubscriberProps(rSubscription: RSubscription): Props =
      Props(classOf[TestObjectSubscriber], rSubscription, this)

    override def replayerProps(rSubscription: RSubscription, objectSubscriber: ActorRef,
      maxMarker: Option[Revision], session: Session): Props = Props.empty
  }

  val caller = TestProbe()
  val deathProbe = TestProbe()
  val requestDelegate = TestProbe()
  val pushChannel = TestProbe()
  val rev: Revision = 1L
  val r: R = R / 'users / 1
  val rSub = RSubscription(ctx, r, Set("name"), rev, requestDelegate.ref, pushChannel.ref)

  val models: List[Model] = List(BaseActorSpec.userModel, BaseActorSpec.carModel,
    BaseActorSpec.companyModel, BaseActorSpec.authorModel, BaseActorSpec.postModel)

  override def beforeAll() = {
    super.beforeAll()
    globals.persistentModelStore ! PersistentModelStore.UpdateModels(models)
    globals.persistentModelStore ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    globals.modelRegistry ! PersistenceUpdate(await = true)
    globals.modelRegistry ! RequestModel.GetModel(BaseActorSpec.userModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.userModel, List.empty))
  }

  "Subscription Manager" should "handle SubscribeOnR, create an ObjectSubscriptionActor and update the internal" +
    " state" in {
      val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
      val subs = subscriptionManger.underlyingActor.rSubscriptions
      subs.isEmpty shouldBe true
      subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub), caller.ref)
      caller.expectMsg(SubscriptionManager.SubscriptionDone(rSub.r))
      subs.size shouldBe 1
      val expectedRSub = subs.get((rSub.pushChannel.path, r)).get
      expectedRSub.r shouldEqual rSub.r
      expectedRSub.objectSubscriber.tell(GetFieldList, caller.ref)
      val msg = caller.expectMsgType[Set[FieldKey]]
      msg shouldEqual Set("name")
    }

  it should "doesn't add same caller twice to subscriptionList" in {
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub), caller.ref)
    caller.expectMsg(SubscriptionManager.SubscriptionDone(rSub.r))
    val rCount = subs.size
    rCount shouldEqual 1
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub), caller.ref)
    subs.size.shouldEqual(rCount)
  }

  it should "handle SubscribeOnObjects correctly for multiple Rs subscription" in {
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    val r1 = R / 'users / 1
    val r2 = R / 'users / 2
    val r3 = R / 'users / 3
    val r4 = R / 'users / 4
    val r5 = r4
    val sub1 = SubscriptionOp(r1, 1L)
    val sub2 = SubscriptionOp(r2, 1L)
    val sub3 = SubscriptionOp(r3, 1L)
    val sub4 = SubscriptionOp(r4, 1L)
    val sub5 = SubscriptionOp(r5, 1L)
    subscriptionManger.tell(Request.SubscribeOnObjects(ctx, SubscriptionBody(List(sub1, sub2, sub3, sub4, sub5)),
      pushChannel.ref), caller.ref)
    caller.expectMsg(Subscribed(Set(r1, r2, r3, r4, r5)))
    subs.size shouldBe 4
    subs.get((pushChannel.ref.path, r1)).get.r shouldBe r1
    subs.get((pushChannel.ref.path, r2)).get.r shouldBe r2
    subs.get((pushChannel.ref.path, r3)).get.r shouldBe r3
    subs.get((pushChannel.ref.path, r4)).get.r shouldBe r4
  }

  it should "allow multiple users to subscribe to a specific resource" in {
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    val r = R / 'users / 1
    val pushChannel1 = TestProbe()
    val pushChannel2 = TestProbe()
    val pushChannel3 = TestProbe()
    val pushChannel4 = TestProbe()

    val sub = SubscriptionOp(r, 1L)
    subscriptionManger.tell(Request.SubscribeOnObjects(ctx, SubscriptionBody(List(sub)),
      pushChannel1.ref), pushChannel1.ref)
    pushChannel1.expectMsg(Subscribed(Set(r)))

    subscriptionManger.tell(Request.SubscribeOnObjects(ctx, SubscriptionBody(List(sub)),
      pushChannel2.ref), pushChannel2.ref)
    pushChannel2.expectMsg(Subscribed(Set(r)))

    subscriptionManger.tell(Request.SubscribeOnObjects(ctx, SubscriptionBody(List(sub)),
      pushChannel3.ref), pushChannel3.ref)
    pushChannel3.expectMsg(Subscribed(Set(r)))

    subscriptionManger.tell(Request.SubscribeOnObjects(ctx, SubscriptionBody(List(sub)),
      pushChannel4.ref), pushChannel4.ref)
    pushChannel4.expectMsg(Subscribed(Set(r)))

    subs.size shouldBe 4
    subs.get((pushChannel1.ref.path, r)).get.r shouldBe r
    subs.get((pushChannel2.ref.path, r)).get.r shouldBe r
    subs.get((pushChannel3.ref.path, r)).get.r shouldBe r
    subs.get((pushChannel4.ref.path, r)).get.r shouldBe r
  }

  it should "handle UnsubscribeFromR, send it to the ObjectSubscriptionActor and remove it from the internal state" in {
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub), caller.ref)
    caller.expectMsg(SubscriptionManager.SubscriptionDone(rSub.r))
    subscriptionManger.underlyingActor.rSubscriptions.isEmpty shouldBe false
    subscriptionManger.underlyingActor.rSubscriptions.size shouldBe 1
    val subscriptionActor = subscriptionManger.underlyingActor.rSubscriptions.toList(0)._2.objectSubscriber
    deathProbe.watch(subscriptionActor)
    subscriptionManger.tell(SubscriptionManager.UnsubscribeFromR(rSub), caller.ref)
    deathProbe.expectTerminated(subscriptionActor)
    subscriptionManger.underlyingActor.rSubscriptions.isEmpty shouldBe true
  }

  it should "handle Update subscription fields" in {
    val rSub1 = RSubscription(ctx, r, Set("name"), rev, requestDelegate.ref, pushChannel.ref)
    val rSub2 = RSubscription(ctx, r, Set("age", "name"), rev, requestDelegate.ref, pushChannel.ref)
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub1), caller.ref)
    caller.expectMsg(SubscriptionManager.SubscriptionDone(rSub.r))
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub2), caller.ref)
    subs.size.shouldEqual(1)
    val expectedRSub = subs.get((rSub1.pushChannel.path, r)).get
    expectedRSub.r shouldEqual rSub1.r
    expectedRSub.objectSubscriber.tell(GetFieldList, caller.ref)
    val msg = caller.expectMsgType[Set[FieldKey]]
    msg shouldEqual Set("name", "age")
  }

}