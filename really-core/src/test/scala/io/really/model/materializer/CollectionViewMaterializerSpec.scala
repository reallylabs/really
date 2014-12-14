/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.materializer

import akka.actor.ActorRef
import akka.persistence.Update
import io.really.fixture.{ PersistentModelStoreFixture, MaterializerTest }
import _root_.io.really.json.collection.JSONCollection
import io.really.model._
import io.really.model.persistent.ModelRegistry.{ ModelOperation, ModelResult, CollectionActorMessage }
import io.really.model.persistent.PersistentModelStore
import io.really.protocol._
import io.really._
import play.api.libs.json._
import reactivemongo.api.Cursor
import scala.concurrent.duration._

import scala.concurrent.Await

class CollectionViewMaterializerSpec extends BaseActorSpecWithMongoDB {

  lazy val collection = globals.mongodbConntection.collection[JSONCollection](s"${BaseActorSpec.authorModel.r.head.collection}")

  def getObject(r: R): Option[JsObject] = {
    val query = Json.obj("_r" -> r)
    val cursor: Cursor[JsObject] = collection.find(query).cursor[JsObject]
    Await.result(cursor.headOption, 5.seconds)
  }

  var modelRouterRef: ActorRef = _
  var modelPersistentActor: ActorRef = _
  val models: List[Model] = List(BaseActorSpec.authorModel, BaseActorSpec.postModel)

  override def beforeAll() {
    super.beforeAll()
    modelRouterRef = globals.modelRegistry
    modelPersistentActor = globals.persistentModelStore

    modelPersistentActor ! PersistentModelStore.UpdateModels(models)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef ! Update(await = true)
    modelRouterRef ! CollectionActorMessage.GetModel(BaseActorSpec.authorModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.authorModel, List.empty))
  }

  "Collection View Materializer" should "read ModelCreated message form journal as first message" in {
    val r = R / 'authors / 123
    val bucketId = Helpers.getBucketIDFromR(r)

    globals.collectionActor ! CollectionActor.GetState(r)
    expectMsg(CommandError.ObjectNotFound(r))

    globals.materializerView ! MaterializerTest.GetState(bucketId)
    expectMsg(CollectionViewMaterializer.MaterializerState(Some(BaseActorSpec.authorModel), Some("ModelCreated"), 1, "with-model"))
  }

  it should "store new object in DB when receive Created event from journal" in {
    val r = R / 'authors / 123
    val bucketId = Helpers.getBucketIDFromR(r)
    val obj = Json.obj("name" -> "Ahmed")
    val req = Request.Create(ctx, r, obj)

    globals.collectionActor ! req
    expectMsgType[Result.CreateResult]

    Thread.sleep(7000)

    globals.materializerView ! MaterializerTest.GetState(bucketId)
    expectMsg(CollectionViewMaterializer.MaterializerState(Some(BaseActorSpec.authorModel), Some("ModelCreated"), 2, "with-model"))

    val o = getObject(r).get
    assert(o.keys == Set("name", "_id", "_r", "_rev", "_metaData"))
    assertResult(1)((o \ "_rev").as[Revision])
    assertResult(r.head.id.get)((o \ "_id").as[Long])
    assertResult(BaseActorSpec.authorModel.collectionMeta.version)((o \ "_metaData" \ "modelVersion").as[ModelVersion])
  }

  it should "update object on DB when receive Updated event from journal" in {
    val r = R / 'authors / 123
    val bucketId = Helpers.getBucketIDFromR(r)
    val req = Request.Update(ctx, r, 1, UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Mohammed"), None))))

    globals.collectionActor ! req
    expectMsgType[Result.UpdateResult]

    Thread.sleep(7000)

    globals.materializerView ! MaterializerTest.GetState(bucketId)
    expectMsg(CollectionViewMaterializer.MaterializerState(Some(BaseActorSpec.authorModel), Some("ModelCreated"), 3, "with-model"))

    val o = getObject(r).get
    assertResult("Mohammed")((o \ "name").as[String])
    assertResult(2)((o \ "_rev").as[Revision])
  }

  it should "update model when receive ModelUpdated from journal" in {
    val r = R / 'authors / 123
    val bucketId = Helpers.getBucketIDFromR(r)
    val newAuthorModel = BaseActorSpec.authorModel.copy(
      collectionMeta = CollectionMetadata(BaseActorSpec.authorModel.collectionMeta.version),
      fields = Map(
        "name" -> ValueField("name", DataType.RString, None, None, true),
        "age" -> ValueField("age", DataType.RLong, None, None, true)
      )
    )

    globals.persistentModelStore ! PersistentModelStore.UpdateModels(List(newAuthorModel))
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(List(newAuthorModel))

    modelRouterRef ! Update(await = true)
    expectMsg(ModelOperation.ModelUpdated(BaseActorSpec.authorModel.r, newAuthorModel, List.empty))
    Thread.sleep(3000)

    globals.materializerView ! MaterializerTest.GetState(bucketId)
    expectMsg(CollectionViewMaterializer.MaterializerState(Some(newAuthorModel), Some("ModelUpdated"), 4, "with-model"))
  }

}
