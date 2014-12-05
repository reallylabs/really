/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.{ ActorRef, Props }
import akka.persistence.{ PersistentView, Update }
import io.really.fixture.PersistentModelStoreFixture
import io.really.fixture.PersistentModelStoreFixture.GetState
import io.really.model.persistent.PersistentModelStore.Models
import org.scalatest.BeforeAndAfterEach
import akka.testkit.TestProbe
import _root_.io.really.model._
import io.really._

class PersistentModelStoreSpec extends BaseActorSpec with BeforeAndAfterEach {

  var persistentActor: ActorRef = _
  var view: ActorRef = _
  var viewProbe: TestProbe = _

  val collMeta: CollectionMetadata = CollectionMetadata(1)

  val profilesR = R / "users"
  val profileModel = Model(profilesR, collMeta, fields,
    JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

  val followersR = R / "users" / "followers"
  val followerModel = Model(followersR, collMeta, fields,
    JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

  val newProfileModel = Model(profilesR, collMeta, fields,
    JsHooks(Some(""), None, None, None, None, None, None), null, List(followerModel.r))

  def fields: Map[FieldKey, Field[_]] = {
    val f1 = ValueField("name", DataType.RString, None, None, true)
    val f2 = ValueField("age", DataType.RLong, None, None, true)
    Map("name" -> f1, "age" -> f2)
  }

  override protected def beforeEach(): Unit = {
    val persistenceId = "model-registry" + Math.random()

    super.beforeEach()
    val persistentActorProps = Props(classOf[PersistentModelStoreFixture], globals, persistenceId)
    persistentActor = system.actorOf(persistentActorProps)

    persistentActor ! PersistentModelStore.UpdateModels(List(profileModel, BaseActorSpec.companyModel, followerModel))
    persistentActor ! GetState
    expectMsg(List(profileModel, BaseActorSpec.companyModel, followerModel))

    viewProbe = TestProbe()
    view = system.actorOf(Props(classOf[PassiveTestPersistentView], persistenceId, viewProbe.ref, None))

    view ! Update(await = true)
    val payload1 = viewProbe.expectMsgType[PersistentModelStore.AddedModels]
    payload1.models.size shouldBe 3
  }

  override protected def afterEach(): Unit = {
    system.stop(persistentActor)
    system.stop(view)
    super.afterEach()
  }

  it should "persist 'AddedModels' event if the models wasn't exist in the state " in {
    persistentActor ! PersistentModelStore.UpdateModels(List(profileModel, BaseActorSpec.companyModel,
      followerModel, BaseActorSpec.postModel))
    persistentActor ! GetState
    val models = expectMsgType[Models]
    models.size shouldBe 4

    view ! Update(await = true)
    val payload = viewProbe.expectMsgType[PersistentModelStore.AddedModels]
    payload.models.size shouldBe 1
  }

  it should "persist 'DeletedModels' event if the models was deleted from the state" in {
    persistentActor ! PersistentModelStore.UpdateModels(List(profileModel, BaseActorSpec.companyModel))
    persistentActor ! GetState
    val models = expectMsgType[Models]
    models.size shouldBe 2

    view ! Update(await = true)
    val payload = viewProbe.expectMsgType[PersistentModelStore.DeletedModels]
    payload.models.size shouldBe 1
  }

  it should "persist 'UpdatedModels' event if the models was exist in the state" in {
    persistentActor ! PersistentModelStore.UpdateModels(List(BaseActorSpec.companyModel, followerModel, newProfileModel))
    persistentActor ! GetState
    val models = expectMsgType[Models]
    models.size shouldBe 3

    view ! Update(await = true)
    val payload = viewProbe.expectMsgType[PersistentModelStore.UpdatedModels]
    payload.models.size shouldBe 1
  }
}

private class PassiveTestPersistentView(name: String, probe: ActorRef, var failAt: Option[String]) extends PersistentView {
  override val persistenceId: String = name
  override val viewId: String = name + "-view"

  override def autoUpdate: Boolean = false

  override def autoUpdateReplayMax: Long = 0L // no message replay during initial recovery

  def receive = {
    case payload if isPersistent =>
      probe ! payload
  }

}

