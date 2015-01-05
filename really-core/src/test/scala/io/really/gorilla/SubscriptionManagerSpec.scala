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

class SubscriptionManagerSpec extends BaseActorSpecWithMongoDB {

  override lazy val globals = new TestReallyGlobals(config, system) {

    override def objectSubscriberProps(rSubscription: RSubscription): Props =
      Props(classOf[TestObjectSubscriber], rSubscription, this)

    override def replayerProps(rSubscription: RSubscription, objectSubscriber: ActorRef,
      maxMarker: Option[Revision]): Props = Props.empty
  }

  val caller = TestProbe()
  val deathProbe = TestProbe()
  val requestDelegate = TestProbe()
  val pushChannel = TestProbe()
  val rev: Revision = 1L
  val r: R = R / 'users / 1
  val rSub = RSubscription(ctx, r, Some(Set("name")), rev, requestDelegate.ref, pushChannel.ref)

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
      caller.expectMsg(SubscriptionManager.SubscriptionDone)
      subs.size shouldBe 1
      val expectedRSub = subs.get(rSub.pushChannel.path).get
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
    caller.expectMsg(SubscriptionManager.SubscriptionDone)
    val rCount = subs.size
    rCount shouldEqual 1
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub), caller.ref)
    subs.size.shouldEqual(rCount)
  }

  it should "handle UnsubscribeOnR, send it to the ObjectSubscriptionActor and remove it from the internal state" in {
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub), caller.ref)
    caller.expectMsg(SubscriptionManager.SubscriptionDone)
    subscriptionManger.underlyingActor.rSubscriptions.isEmpty shouldBe false
    subscriptionManger.underlyingActor.rSubscriptions.size shouldBe 1
    val subscriptionActor = subscriptionManger.underlyingActor.rSubscriptions.toList(0)._2.objectSubscriber
    deathProbe.watch(subscriptionActor)
    subscriptionManger.tell(SubscriptionManager.UnsubscribeFromR(rSub), caller.ref)
    deathProbe.expectTerminated(subscriptionActor)
    subscriptionManger.underlyingActor.rSubscriptions.isEmpty shouldBe true
  }

  it should "handle Update subscription fields" in {
    val rSub1 = RSubscription(ctx, r, Some(Set("name")), rev, requestDelegate.ref, pushChannel.ref)
    val rSub2 = RSubscription(ctx, r, Some(Set("age", "name")), rev, requestDelegate.ref, pushChannel.ref)
    val subscriptionManger = TestActorRef[SubscriptionManager](globals.subscriptionManagerProps)
    val subs = subscriptionManger.underlyingActor.rSubscriptions
    subs.isEmpty shouldBe true
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub1), caller.ref)
    caller.expectMsg(SubscriptionManager.SubscriptionDone)
    subscriptionManger.tell(SubscriptionManager.SubscribeOnR(rSub2), caller.ref)
    subs.size.shouldEqual(1)
    val expectedRSub = subs.get(rSub1.pushChannel.path).get
    expectedRSub.r shouldEqual rSub1.r
    expectedRSub.objectSubscriber.tell(GetFieldList, caller.ref)
    val msg = caller.expectMsgType[Set[FieldKey]]
    msg shouldEqual Set("name", "age")
  }
}