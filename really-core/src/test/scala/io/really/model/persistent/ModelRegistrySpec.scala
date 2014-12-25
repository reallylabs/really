/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.{ ActorRef, Props }
import akka.persistence.Update
import io.really.fixture.PersistentModelStoreFixture
import io.really.model.persistent.ModelRegistry.RequestModel.GetModel
import io.really.model.persistent.ModelRegistry.{ ModelOperation, ModelResult }
import io.really.{ R, BaseActorSpec }
import io.really.model._
import org.scalatest.BeforeAndAfterEach

class ModelRegistrySpec extends BaseActorSpec with BeforeAndAfterEach {

  val collMeta: CollectionMetadata = CollectionMetadata(1)
  var persistentActor: ActorRef = _
  var modelRouterRef: ActorRef = _

  def fields: Map[FieldKey, Field[_]] = {
    val f1 = ValueField("name", DataType.RString, None, None, true)
    val f2 = ValueField("age", DataType.RLong, None, None, true)
    Map("name" -> f1, "age" -> f2)
  }

  val profilesR = R / "users"
  val profileModel = Model(profilesR, collMeta, fields,
    JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

  override protected def beforeEach(): Unit = {
    val persistenceId = "model-registry" + Math.random()

    super.beforeEach()
    val models = List(profileModel)
    val persistentActorProps = Props(classOf[PersistentModelStoreFixture], globals, persistenceId)
    persistentActor = system.actorOf(persistentActorProps)

    persistentActor ! PersistentModelStore.UpdateModels(models)
    persistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef = system.actorOf(Props(classOf[ModelRegistry], globals, persistenceId))

    modelRouterRef ! Update(await = true)
    modelRouterRef ! GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))
  }

  override protected def afterEach(): Unit = {
    system.stop(persistentActor)
    system.stop(modelRouterRef)
    super.afterEach()
  }

  "Model Registry Router" should "handle model added event" in {

    modelRouterRef ! GetModel(BaseActorSpec.postModel.r, self)
    expectMsg(ModelResult.ModelNotFound)
    val models = List(profileModel, BaseActorSpec.postModel)
    persistentActor ! PersistentModelStore.UpdateModels(models)
    persistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef ! Update(await = true)
    modelRouterRef ! GetModel(BaseActorSpec.postModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.postModel, List.empty))
  }

  it should "handle model updated event" in {
    //get profile model
    modelRouterRef ! GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))

    //update models
    val f1 = ValueField("firstName", DataType.RString, None, None, true)
    val f2 = ValueField("lastName", DataType.RString, None, None, true)
    val f3 = ValueField("age", DataType.RLong, None, None, true)

    val newProfileModel = Model(profilesR, collMeta, Map("firstName" -> f1, "lastName" -> f2, "age" -> f3),
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    persistentActor ! PersistentModelStore.UpdateModels(List(newProfileModel))
    persistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(List(newProfileModel))

    modelRouterRef ! Update(await = true)
    expectMsg(ModelOperation.ModelUpdated(profilesR, newProfileModel, List.empty))
  }

  it should "handle model deleted event" in {
    //get profile model
    modelRouterRef ! GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))

    //delete users model
    persistentActor ! PersistentModelStore.UpdateModels(List(BaseActorSpec.postModel))
    persistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(List(BaseActorSpec.postModel))

    modelRouterRef ! Update(await = true)

    expectMsg(ModelOperation.ModelDeleted(profilesR))

    modelRouterRef ! GetModel(profilesR, self)
    expectMsg(ModelResult.ModelNotFound)
  }

  it should "handle get model using `r` that has Id" in {
    //get profile model
    modelRouterRef ! GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))

    // try get profile model by specific profile R
    modelRouterRef ! GetModel(R("/users/1213213"), self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))
  }

}