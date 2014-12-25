/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.{ ActorSystem, Props }
import akka.testkit.TestProbe
import io.really._
import _root_.io.really.model._
import _root_.io.really.protocol._
import _root_.io.really.rql.RQL

import play.api.libs.json.Json

class RequestRouterSpec extends BaseActorSpec {

  val collMeta: CollectionMetadata = CollectionMetadata(1)

  def fields: Map[FieldKey, Field[_]] = {
    val f1 = ValueField("name", DataType.RString, None, None, true)
    val f2 = ValueField("age", DataType.RLong, None, None, true)
    Map("name" -> f1, "age" -> f2)
  }

  override val globals = new RequestRouterGlobals(config, system)

  val accountsR = R / "profiles"
  val accountsModel = Model(accountsR, collMeta, fields,
    JsHooks(
      Some(""),
      None,
      None,
      None,
      None,
      None,
      None
    ), null, List.empty)

  val boardsR = R / "boards"
  val boardsModel = Model(boardsR, collMeta, fields,
    JsHooks(
      Some(""),
      None,
      None,
      None,
      None,
      None,
      None
    ), null, List.empty)

  val models = List(accountsModel, boardsModel)
  val requestRouterRef = system.actorOf(Props(new RequestRouter(globals, "model-registry-persistent-test")))
  requestRouterRef ! PersistentModelStore.AddedModels(models)

  "Request Router" should "return error if required R not listed in models" in {
    val r = R / "images" / 123
    requestRouterRef ! Request.Create(
      ctx,
      r,
      Json.obj(
        "name" -> "Hatem",
        "age" -> "29"
      )
    )
    expectMsgType[RequestRouter.RequestRouterResponse.RNotFound]
  }

  it should "forward message to read handler if command is Get" in {
    val r = R / "boards" / 3
    val req = Request.Get(ctx, r, null)
    requestRouterRef ! req
    globals.readHandlerTestProps.expectMsg(req)
  }

  it should "forward message to read handler if command is Read" in {
    val r = R / "boards"
    val req = Request.Read(ctx, r, ReadOpts(Set.empty, RQL.EmptyQuery, 10, false, None, 0, false, false))
    requestRouterRef ! req
    globals.readHandlerTestProps.expectMsg(req)
  }

  it should "forward message to collection actor if command is Create" in {
    val r = R / "boards"
    val req = Request.Create(ctx, r, Json.obj("name" -> "Hatem", "age" -> "30"))
    requestRouterRef ! req
    val forwardReq = globals.collectionActorTestProps.expectMsgType[Request.Create]
    forwardReq.r.isObject shouldEqual true
  }

  it should "forward message to collection actor if command is Update" in {
    val r = R / "boards" / 3
    val req = Request.Update(ctx, r, 12, null)
    requestRouterRef ! req
    globals.collectionActorTestProps.expectMsg(req)
  }

  it should "forward message to collection actor if command is Delete" in {
    val r = R / "boards" / 3
    val req = Request.Delete(ctx, r)
    requestRouterRef ! req
    globals.collectionActorTestProps.expectMsg(req)
  }

  it should "forward message to subscription manager if command is Subscribe" in {
    val req = Request.Subscribe(ctx, SubscriptionBody(List.empty))
    requestRouterRef ! req
    globals.subscriptionManagerTestProps.expectMsg(req)
  }

  it should "forward message to subscription manager if command is Unsubscribe" in {
    val req = Request.Unsubscribe(ctx, UnsubscriptionBody(List.empty))
    requestRouterRef ! req
    globals.subscriptionManagerTestProps.expectMsg(req)
  }

  it should "forward message to subscription manager if command id GetSubscription" in {
    val r = R / "boards" / 45
    val req = Request.GetSubscription(ctx, r)
    requestRouterRef ! req
    globals.subscriptionManagerTestProps.expectMsg(req)
  }

}

class RequestRouterGlobals(override val config: ReallyConfig, override val actorSystem: ActorSystem) extends TestReallyGlobals(config, actorSystem) {
  val collectionActorTestProps = TestProbe()(actorSystem)
  val subscriptionManagerTestProps = TestProbe()(actorSystem)
  val readHandlerTestProps = TestProbe()(actorSystem)

  override lazy val subscriptionManager = subscriptionManagerTestProps.testActor

  override lazy val readHandler = readHandlerTestProps.testActor

  override lazy val collectionActor = collectionActorTestProps.testActor
}
