/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import akka.actor.Props
import io.really.model.persistent.ModelRegistry.{ ModelOperation, ModelResult, CollectionActorMessage }
import io.really.{ R, BaseActorSpec }
import io.really.model._

class ModelRegistrySpec extends BaseActorSpec {

  val collMeta: CollectionMetadata = CollectionMetadata(1)

  def fields: Map[FieldKey, Field[_]] = {
    val f1 = ValueField("name", DataType.RString, None, None, true)
    val f2 = ValueField("age", DataType.RLong, None, None, true)
    Map("name" -> f1, "age" -> f2)
  }

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
  val modelRouterRef = system.actorOf(Props(new ModelRegistry(globals)))
  modelRouterRef ! PersistentModelStore.AddedModels(models)

  "Model Registry Router" should "handle model added event" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistry(globals)))

    modelRouterRef ! CollectionActorMessage.GetModel(profilesR, self)
    expectMsg(ModelResult.ModelNotFound)

    modelRouterRef ! PersistentModelStore.AddedModels(models)

    modelRouterRef ! CollectionActorMessage.GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))
  }

  it should "handle model updated event" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistry(globals)))
    modelRouterRef ! PersistentModelStore.AddedModels(models)

    //get profile model
    modelRouterRef ! CollectionActorMessage.GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))

    //update models
    val f1 = ValueField("firstName", DataType.RString, None, None, true)
    val f2 = ValueField("lastName", DataType.RString, None, None, true)
    val f3 = ValueField("age", DataType.RLong, None, None, true)

    val newProfileModel = Model(profilesR, collMeta, Map("firstName" -> f1, "lastName" -> f2, "age" -> f3),
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)
    modelRouterRef ! PersistentModelStore.UpdatedModels(List(newProfileModel))

    expectMsg(ModelOperation.ModelUpdated(profilesR, newProfileModel, List.empty))
  }

  it should "handle model deleted event" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistry(globals)))
    modelRouterRef ! PersistentModelStore.AddedModels(models)

    //get profile model
    modelRouterRef ! CollectionActorMessage.GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))

    //delete users model
    modelRouterRef ! PersistentModelStore.DeletedModels(models)

    expectMsg(ModelOperation.ModelDeleted(profilesR))

    modelRouterRef ! CollectionActorMessage.GetModel(profilesR, self)
    expectMsg(ModelResult.ModelNotFound)
  }

  it should "handle get model" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistry(globals)))
    modelRouterRef ! PersistentModelStore.AddedModels(models)

    //get profile model
    modelRouterRef ! CollectionActorMessage.GetModel(profilesR, self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))

    // try get profile model by specific profile R
    modelRouterRef ! CollectionActorMessage.GetModel(R("/users/1213213"), self)
    expectMsg(ModelResult.ModelObject(profileModel, List.empty))
  }

}
