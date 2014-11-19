/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.Props
import akka.persistence.Update
import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import io.really.model._
import io.really.{ BaseActorSpec, R, ReallyConfig, TestConf }

class PersistentModelStoreSpec(conf: ReallyConfig) extends BaseActorSpec(conf) {

  def this() = this(new ReallyConfig(ConfigFactory.parseString("akka.persistence.view.auto-update-interval = 1s").withFallback(TestConf.getConfig().getRawConfig)))

  val persistentModel = system.actorOf(Props(new PersistentModelStore(globals)))

  val modelRegistry = system.actorOf(Props(new ModelRegistry(globals)))

  val collMeta: CollectionMetadata = CollectionMetadata(1)

  val profilesR = R / "users"
  val profileModel = Model(profilesR, collMeta, fields,
    JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

  val boardsR = R / "boards"
  val boardModel = Model(boardsR, collMeta, fields,
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

  "Persistent Model Store" should "add models to state if receive updateModels" in {
    //send update models to persistent model
    persistentModel ! PersistentModelStore.UpdateModels(List(profileModel))

    //force view to update state
    modelRegistry ! Update(await = true)

    Thread.sleep(6000)

    //send GetModel to ModelRegistryRouter
    modelRegistry ! ModelRegistry.CollectionActorMessage.GetModel(profilesR, self)

    expectMsg(ModelRegistry.ModelResult.ModelObject(profileModel, List.empty))

    //send update models to persistent model with new models
    persistentModel ! PersistentModelStore.UpdateModels(List(profileModel, boardModel))

    //force view to update state
    modelRegistry ! Update(await = true)

    Thread.sleep(6000)

    //send GetModel for board to ModelRegistryRouter
    modelRegistry ! ModelRegistry.CollectionActorMessage.GetModel(boardsR, self)

    expectMsg(ModelRegistry.ModelResult.ModelObject(boardModel, List.empty))

    //send update models to persistent model with changed models and remove some models
    persistentModel ! PersistentModelStore.UpdateModels(List(newProfileModel))

    //force view to update state
    modelRegistry ! Update(await = true)

    Thread.sleep(6000)

    expectMsg(ModelRegistry.ModelOperation.ModelUpdated(profilesR, newProfileModel, List.empty))

    expectMsg(ModelRegistry.ModelOperation.ModelDeleted(boardsR))
  }

  it should "calculate changed models" in {
    val persistent: PersistentModelStore = TestActorRef(Props(new PersistentModelStore(globals))).underlyingActor

    val oldModels = List(profileModel, boardModel)
    val newModels = List(newProfileModel, boardModel)

    val changedModel = persistent.getChangedModels(newModels, oldModels)

    assert(changedModel == List(newProfileModel))
  }

}
