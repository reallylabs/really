/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.materializer

import akka.actor.{ ActorRef, Props }
import akka.persistence.{ Update, RecoveryCompleted }
import akka.testkit.{ TestProbe, TestActorRef }
import io.really.fixture.{ CollectionActorTest, PersistentModelStoreFixture, MaterializerTest }
import _root_.io.really.json.collection.JSONCollection
import io.really.model._
import io.really.model.persistent.ModelRegistry.RequestModel.GetModel
import io.really.model.persistent.ModelRegistry.{ ModelOperation, ModelResult }
import io.really.model.persistent.PersistentModelStore
import io.really.protocol._
import io.really._
import play.api.libs.json._
import reactivemongo.api.Cursor
import scala.concurrent.duration._

import scala.concurrent.Await

class CollectionViewMaterializerSpec extends BaseActorSpecWithMongoDB {

  lazy val collection = globals.mongodbConnection.collection[JSONCollection](s"${BaseActorSpec.authorModel.r.head.collection}")
  val modelVersion = 23

  def getObject(r: R): Option[JsObject] = {
    val query = Json.obj("_r" -> r)
    val cursor: Cursor[JsObject] = collection.find(query).cursor[JsObject]
    Await.result(cursor.headOption, 5.seconds)
  }

  var modelRouterRef: ActorRef = _
  var modelPersistentActor: ActorRef = _
  val models: List[Model] = List(BaseActorSpec.authorModel, BaseActorSpec.postModel)

  def notifyMaterializer(materializer: (ActorRef, TestProbe)) = {
    materializer._1 ! akka.persistence.Update(await = true)
  }

  def getPersistentActor(bucketId: BucketID) = {
    val testProbe = TestProbe()
    val fakePersistentActor = system.actorOf(TestActors.reportingPersistentActorProps(bucketId, testProbe.ref))
    (fakePersistentActor, testProbe)
  }

  def getMaterializer(bucketId: BucketID) = {
    val probe = TestProbe()
    val actor = TestActorRef(Props(classOf[MaterializerTest], globals, probe.ref), bucketId)
    (actor, probe)
  }

  def createModel(r: R, persistentActor: (ActorRef, TestProbe)) = {
    val modelCreatedEvent = ModelOperation.ModelCreated(r, BaseActorSpec.authorModel, Nil)
    persistentActor._1 ! ReportingPersistentActor.Persist(modelCreatedEvent)
    persistentActor._2.expectMsg(ReportingPersistentActor.Persisted(modelCreatedEvent))
  }

  def createObject(r: R, persistentActor: (ActorRef, TestProbe)) = {
    val obj = Json.obj("name" -> "Ahmed")
    val fullObj = obj ++ Json.obj("_rev" -> 1, "_r" -> r)
    val event = CollectionActor.CollectionActorEvent.Created(r, fullObj, modelVersion, ctx)
    persistentActor._1 ! ReportingPersistentActor.Persist(event)
    persistentActor._2.expectMsg(ReportingPersistentActor.Persisted(event))
  }

  def deleteObject(r: R, persistentActor: (ActorRef, TestProbe)) = {
    val event = CollectionActor.CollectionActorEvent.Deleted(r, 3, modelVersion, ctx)
    persistentActor._1 ! ReportingPersistentActor.Persist(event)
  }

  override def beforeAll() {
    super.beforeAll()
    modelRouterRef = globals.modelRegistry
    modelPersistentActor = globals.persistentModelStore

    modelPersistentActor ! PersistentModelStore.UpdateModels(models)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef ! Update(await = true)
    modelRouterRef ! GetModel(BaseActorSpec.authorModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.authorModel, List.empty))
  }

  override val globals = new TestReallyGlobals(config, system) {
    val materializerProbe = TestProbe()
    override val materializerProps = Props(classOf[MaterializerTest], this, materializerProbe.ref)
  }

  "Collection View Materializer" should "read ModelCreated message form journal as first message" in {
    val r = R / 'authors / 123
    val bucketId = Helpers.getBucketIDFromR(r)

    globals.collectionActor ! CollectionActorTest.GetState(r)
    expectMsg(CollectionActor.ObjectNotFound(r))

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

  it should "delete object from DB when receive Deleted event from journal" in {
    val r = R / 'authors / 123
    val bucketId = Helpers.getBucketIDFromR(r)

    val p @ (pActor, pProbe) = getPersistentActor(bucketId)
    pProbe.expectMsgType[ReportingPersistentActor.ReceivedRecover]
    pProbe.expectMsgType[ReportingPersistentActor.ReceivedRecover]
    pProbe.expectMsgType[ReportingPersistentActor.ReceivedRecover]
    pProbe.expectMsgType[ReportingPersistentActor.ReceivedRecover]
    val m @ (mActor, mProbe) = getMaterializer(bucketId)

    notifyMaterializer(m)
    mProbe.expectMsgType[akka.persistence.SnapshotOffer]
    mProbe.expectNoMsg()

    deleteObject(r, p)
    pProbe.expectMsgType[ReportingPersistentActor.Persisted].event.asInstanceOf[CollectionActor.CollectionActorEvent.Deleted]
    pProbe.expectNoMsg()

    notifyMaterializer(m)
    Thread.sleep(1000)
    mProbe.expectMsgType[CollectionActor.CollectionActorEvent.Deleted](duration)

    val o = getObject(r).get
    assertResult(true)((o \ "_deleted").as[Boolean])
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
