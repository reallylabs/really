package io.really.model

import io.really._
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import io.really.model.ModelRegistryRouter.{ModelOperation, ModelResult, CollectionActorMessage}
import io.really.quickSand.QuickSand
import play.api.libs.json.Json

class ModelRegistryRouterSpec extends BaseActorSpec {

  val collMeta: CollectionMetadata = CollectionMetadata(1)

  def fields: Map[FieldKey, Field[_]] = {
    val f1 = ValueField("name", DataType.RString, None, None, true)
    val f2 = ValueField("age", DataType.RLong, None, None, true)
    Map("name" -> f1, "age" -> f2)
  }

  override val globals = new ModelRouterGlobals(config, system)

  val accountsR = R / "profiles"
  val accountsModel = Model(accountsR, collMeta, fields,
    JsHooks(Some(""),
      None,
      None,
      None,
      None,
      None,
      None), null, List.empty)

  val boardsR = R / "boards"
  val boardsModel = Model(boardsR, collMeta, fields,
    JsHooks(Some(""),
      None,
      None,
      None,
      None,
      None,
      None), null, List.empty)

  val models = List(accountsModel,boardsModel)
  val modelRouterRef = system.actorOf(Props(new ModelRegistryRouter(globals)))
  modelRouterRef ! PersistentModelStore.AddedModels(models)

  "Model Registry Router" should "handle model added event" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistryRouter(globals)))

    modelRouterRef ! CollectionActorMessage.GetModel(profilesR)
    expectMsg(ModelResult.ModelNotFound)

    modelRouterRef ! PersistentModelStore.AddedModels(models)

    modelRouterRef ! CollectionActorMessage.GetModel(profilesR)
    expectMsg(ModelResult.ModelObject(profileModel))
  }

  it should "handle model updated event" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistryRouter(globals)))
    modelRouterRef ! PersistentModelStore.AddedModels(models)

    //get profile model
    modelRouterRef ! CollectionActorMessage.GetModel(profilesR)
    expectMsg(ModelResult.ModelObject(profileModel))

    //update models
    val f1 = ValueField("firstName", DataType.RString, None, None, true)
    val f2 = ValueField("lastName", DataType.RString, None, None, true)
    val f3 = ValueField("age", DataType.RLong, None, None, true)

    val newProfileModel = Model(profilesR, collMeta, Map("firstName" -> f1, "lastName" -> f2, "age" -> f3),
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)
    modelRouterRef ! PersistentModelStore.UpdatedModels(List(newProfileModel))

    expectMsg(ModelOperation.ModelUpdated(profilesR, newProfileModel))
  }

  it should "handle model deleted event" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistryRouter(globals)))
    modelRouterRef ! PersistentModelStore.AddedModels(models)

    //get profile model
    modelRouterRef ! CollectionActorMessage.GetModel(profilesR)
    expectMsg(ModelResult.ModelObject(profileModel))

    //delete users model
    modelRouterRef ! PersistentModelStore.DeletedModels(models)

    expectMsg(ModelOperation.ModelDeleted(profilesR))

    modelRouterRef ! CollectionActorMessage.GetModel(profilesR)
    expectMsg(ModelResult.ModelNotFound)
  }

  it should "handle get model" in {
    val profilesR = R / "users"
    val profileModel = Model(profilesR, collMeta, fields,
      JsHooks(Some(""), None, None, None, None, None, None), null, List.empty)

    val models = List(profileModel)

    val modelRouterRef = system.actorOf(Props(new ModelRegistryRouter(globals)))
    modelRouterRef ! PersistentModelStore.AddedModels(models)

    //get profile model
    modelRouterRef ! CollectionActorMessage.GetModel(profilesR)
    expectMsg(ModelResult.ModelObject(profileModel))
  }

  it should "return error if required R not listed in models" in {
    val r = R / "images" /123
    modelRouterRef ! Request.Create(
      ctx,
      r ,
      Json.obj(
        "name" -> "Hatem",
        "age" -> "29"))
    expectMsgType[ModelRegistryRouter.ModelRouterResponse.RNotFound]
  }

  it should "return unsupported command error in case of invalid command" in {
    val r = R / "users" /123/"boards"/3
    modelRouterRef ! Request.Get(
      ctx,
      r ,
      null)
    expectMsgType[ModelRegistryRouter.ModelRouterResponse.UnsupportedCmd]
  }

  it should "forward message to collection actor if command is Create" in {
    val r = R / "boards" / 3
    val req = Request.Create(ctx, r, Json.obj("name" -> "Hatem", "age" -> "30"))
    modelRouterRef ! req
    globals.collectionProps.expectMsg(req)
  }

  it should "forward message to collection actor if command is Update" in {
    val r = R / "boards" / 3
    val req = Request.Update(ctx, r, 12, null)
    modelRouterRef ! req
    globals.collectionProps.expectMsg(req)
  }


  it should "forward message to collection actor if command is Delete" in {
    val r = R / "boards" / 3
    val req = Request.Delete(ctx, r)
    modelRouterRef ! req
    globals.collectionProps.expectMsg(req)
  }

}

class ModelRouterGlobals(override val config: ReallyConfig, override val actorSystem: ActorSystem) extends TestReallyGlobals(config, actorSystem) {
  lazy val collectionProps: TestProbe = TestProbe()(actorSystem)

  override def boot() = {
    receptionist_.set(actorSystem.actorOf(receptionistProps, "requests"))
    quickSand_.set(new QuickSand(config, actorSystem))
    modelRegistry_.set(actorSystem.actorOf(modelRegistryRouterProps, "model-registry"))
    collectionActor_.set(collectionProps.ref)
  }
}