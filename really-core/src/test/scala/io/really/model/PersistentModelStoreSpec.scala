package io.really.model

import akka.actor.Props
import akka.persistence.Update
import akka.testkit.TestActorRef
import io.really.{R, BaseActorSpec}


class PersistentModelStoreSpec extends BaseActorSpec {

  val persistentModel = system.actorOf(Props(new PersistentModelStore(globals)))

  val modelRouter = system.actorOf(Props(new ModelRegistryRouter(globals)))

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
    //send GetModel to ModelRegistryRouter
    modelRouter ! ModelRegistryRouter.CollectionActorMessage.GetModel(profilesR)

    expectMsg(ModelRegistryRouter.ModelResult.ModelNotFound)

    //send update models to persistent model
    persistentModel ! PersistentModelStore.UpdateModels(List(profileModel))

    //force view to update state
    modelRouter ! Update(await = true)

    Thread.sleep(10000)

    //send GetModel to ModelRegistryRouter
    modelRouter ! ModelRegistryRouter.CollectionActorMessage.GetModel(profilesR)

    expectMsg(ModelRegistryRouter.ModelResult.ModelObject(profileModel))
  }

  it should "add models to state if receive updateModels second time" in {
    //send update models to persistent model
    persistentModel ! PersistentModelStore.UpdateModels(List(profileModel, boardModel))

    //force view to update state
    modelRouter ! Update(await = true)

    Thread.sleep(10000)

    //send GetModel for board to ModelRegistryRouter
    modelRouter ! ModelRegistryRouter.CollectionActorMessage.GetModel(boardsR)

    expectMsg(ModelRegistryRouter.ModelResult.ModelObject(boardModel))
  }

  it should "update model that changed" in {
    //send update models to persistent model
    persistentModel ! PersistentModelStore.UpdateModels(List(newProfileModel, boardModel))

    //force view to update state
    modelRouter ! Update(await = true)

    Thread.sleep(10000)

    expectMsg(ModelRegistryRouter.ModelOperation.ModelUpdated(profilesR, newProfileModel))
  }

  it should "remove model from state that deleted" in {
    //send update models to persistent model
    persistentModel ! PersistentModelStore.UpdateModels(List(boardModel))

    //force view to update state
    modelRouter ! Update(await = true)

    Thread.sleep(10000)

    expectMsg(ModelRegistryRouter.ModelOperation.ModelDeleted(R / "users"))
  }

  it should "calculate changed models" in {
    val persistent: PersistentModelStore = TestActorRef(Props(new PersistentModelStore(globals))).underlyingActor

    val oldModels = List(profileModel, boardModel)
    val newModels = List(newProfileModel, boardModel)

    val changedModel = persistent.getChangedModels(newModels, oldModels)

    assert(changedModel == List(newProfileModel))
  }

}
