/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor.{ ActorRef, ActorSystem, PoisonPill, Props }
import akka.testkit.{ TestActorRef, TestProbe }
import akka.persistence.{ Update => PersistenceUpdate }
import io.really.CommandError.ParentNotFound
import io.really.Request.{ Update, Delete, Create }
import io.really.Result.{ UpdateResult, CreateResult }
import io.really.fixture.CollectionActorTest.GetState
import io.really.fixture.{ CollectionActorTest, PersistentModelStoreFixture }
import io.really.model.CollectionActor.{ GetExistenceState, State }
import io.really.model.persistent.ModelRegistry.ModelResult.ModelObject
import io.really.model.persistent.{ PersistentModelStore }
import io.really.model.persistent.ModelRegistry.{ RequestModel, ModelOperation, ModelResult }
import io.really.protocol.{ UpdateCommand, UpdateOp, UpdateBody }
import io.really.CommandError.InvalidCommand
import io.really._
import org.scalatest.{ BeforeAndAfterEach }
import play.api.libs.json.{ JsNumber, Json }
import scala.concurrent.duration._

import play.api.data.validation.ValidationError
import play.api.libs.json._

class CollectionActorSpec extends BaseActorSpec with BeforeAndAfterEach {

  override val globals = new CollectionTestReallyGlobals(config, system)

  case class UnsupportedCommand(r: R, cmd: String) extends RoutableToCollectionActor

  var modelRegistryRef: ActorRef = _
  var modelPersistentActor: ActorRef = _
  val models: List[Model] = List(BaseActorSpec.userModel, BaseActorSpec.carModel,
    BaseActorSpec.companyModel, BaseActorSpec.authorModel, BaseActorSpec.postModel)

  override def beforeEach(): Unit = {
    modelRegistryRef = globals.modelRegistry
    modelPersistentActor = globals.persistentModelStore

    val testProp = TestProbe()

    modelPersistentActor ! PersistentModelStore.UpdateModels(models)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    val persistentState = expectMsgType[List[Model]]
    assert(persistentState canEqual models)

    modelRegistryRef ! PersistenceUpdate(await = true)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, testProp.ref), testProp.ref)
    testProp.fishForMessage(3.seconds) {
      case msg: ModelResult.ModelObject =>
        msg == ModelResult.ModelObject(BaseActorSpec.userModel, List.empty)
      case _ => false
    }

  }

  "CollectionActor" should "get ModelNotFound for any supported request for unknown model" in {
    val r = R / 'unknownModel / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(GetState(r), probe.ref)
    val em = probe.expectMsgType[ModelResult]
    em should be(ModelResult.ModelNotFound)
  }

  it should "get ObjectNotFound for uncreated yet objects" in {
    val obj = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(GetState(obj), probe.ref)
    val em = probe.expectMsgType[CollectionActor.ObjectNotFound]
    em should be(CollectionActor.ObjectNotFound(obj))
  }

  it should "should have the correct BucketID, PersistenceID and R" in {
    val obj = R / 'posts / globals.quickSand.nextId()
    val bucketID = Helpers.getBucketIDFromR(obj)
    val collectionActor = TestActorRef[CollectionActor](Props(new CollectionActor(globals)), bucketID).underlyingActor
    collectionActor.bucketId should be(bucketID)
    collectionActor.persistenceId should be(bucketID)
    collectionActor.r should be(obj.skeleton)
  }

  it should "update the internal model state when it receives the ModelUpdated message" in {
    val r = R / 'users / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "name" shouldBe JsString("Hatem AlSum")
    res.body \ "age" shouldBe JsNumber(30)
    res.body \ "_rev" shouldBe JsNumber(1)

    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("address" -> ValueField("address", DataType.RString, None, None, true))
    )

    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel)), probe.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, probe.ref)
    val result = probe.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), probe.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, probe.ref), probe.ref)
    probe.expectMsg(ModelObject(newUserModel, List()))

    val rx = R / 'users / globals.quickSand.nextId()
    val userObjx = Json.obj("name" -> "Ahmed Refaey", "age" -> 23)
    globals.collectionActor.tell(Create(ctx, rx, userObjx), probe.ref)
    probe.expectMsgType[CommandError.ModelValidationFailed]
  }

  it should "invalidate the internal state when it receives the ModelDeleted message" in {
    val r = R / 'companies / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Foo Bar", "employees" -> 43)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "name" shouldBe JsString("Foo Bar")
    //get company model
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.companyModel.r, probe.ref), probe.ref)
    probe.expectMsg(ModelResult.ModelObject(BaseActorSpec.companyModel, List.empty))

    //delete company model
    val newModels = models.filterNot(_.r == BaseActorSpec.companyModel.r)
    modelPersistentActor ! PersistentModelStore.UpdateModels(newModels)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(newModels)

    modelRegistryRef ! PersistenceUpdate(await = true)
    probe.expectMsg(ModelOperation.ModelDeleted(BaseActorSpec.companyModel.r))

    val userObjx = Json.obj("name" -> "Cloud9ers", "employees" -> 23)
    val rx = R / 'companies / globals.quickSand.nextId()
    globals.collectionActor.tell(Create(ctx, rx, userObj), probe.ref)
    val resx = probe.expectMsgType[ModelResult]
    resx should be(ModelResult.ModelNotFound)

    globals.collectionActor.tell(GetExistenceState(rx), probe.ref)
    val em1 = probe.expectMsgType[ModelResult]
    em1 should be(ModelResult.ModelNotFound)

    globals.collectionActor.tell(Create(ctx, rx, Json.obj()), probe.ref)
    val em2 = probe.expectMsgType[ModelResult]
    em2 should be(ModelResult.ModelNotFound)

    globals.collectionActor.tell(Update(ctx, rx, 0, null), probe.ref)
    val em3 = probe.expectMsgType[ModelResult]
    em3 should be(ModelResult.ModelNotFound)

    globals.collectionActor.tell(Delete(ctx, rx), probe.ref)
    val em4 = probe.expectMsgType[ModelResult]
    em4 should be(ModelResult.ModelNotFound)

    globals.collectionActor.tell(UnsupportedCommand(rx, "UNKNOWN CMD"), probe.ref)
    val em5 = probe.expectMsgType[ModelResult]
    em5 should be(ModelResult.ModelNotFound)
  }

  it should "reply with InvalidCommand if an unknown command was sent" in {
    val r = R / 'users / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Foo Bar", "age" -> 38)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    globals.collectionActor.tell(UnsupportedCommand(r, "UNKNOWN CMD"), probe.ref)
    val resx = probe.expectMsgType[InvalidCommand]
    resx.reason should include("UNKNOWN CMD")
  }

  "Recovery" should "correctly retrieve the previously saved objects" in {
    val r = R / 'users / globals.quickSand.nextId()
    val actorName = Helpers.getBucketIDFromR(r)
    val collectionActor = system.actorOf(Props(new CollectionActorTest(globals)), actorName)
    val userObj = Json.obj("name" -> "Montaro", "age" -> 23)
    val probe = TestProbe()
    collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "name" shouldBe JsString("Montaro")
    collectionActor.tell(GetState(r), probe.ref)
    val state = probe.expectMsgType[CollectionActor.State]
    state.obj \ "name" shouldBe JsString("Montaro")
    state.obj \ "age" shouldBe JsNumber(23)

    probe watch collectionActor
    collectionActor ! PoisonPill
    probe.expectTerminated(collectionActor)

    val collectionActorx = system.actorOf(Props(new CollectionActorTest(globals)), actorName)
    collectionActorx.tell(GetState(r), probe.ref)
    val statex = probe.expectMsgType[CollectionActor.State]
    statex.obj \ "name" shouldBe JsString("Montaro")
    statex.obj \ "age" shouldBe JsNumber(23)
  }

  it should "correctly retrieve the previously updated objects" in {
    val r = R / 'users / globals.quickSand.nextId()
    val actorName = Helpers.getBucketIDFromR(r)
    val collectionActor = system.actorOf(Props(new CollectionActorTest(globals)), actorName)
    val probe = TestProbe()
    collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[CollectionActor.State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
    collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
    collectionActor.tell(GetState(r), probe.ref)
    val state = probe.expectMsgType[CollectionActor.State]
    probe watch collectionActor
    collectionActor ! PoisonPill
    probe.expectTerminated(collectionActor)
    val collectionActorx = system.actorOf(Props(new CollectionActorTest(globals)), actorName)
    collectionActorx.tell(GetState(r), probe.ref)
    val statex = probe.expectMsgType[State]
    state shouldEqual statex
    (state.obj \ "name").as[String] shouldEqual "Amal"

  }

  "Create Command" should "create object sucessfully" in {
    val r = R / 'users / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "name" shouldBe JsString("Hatem AlSum")
    res.body \ "age" shouldBe JsNumber(30)
    res.body \ "_rev" shouldBe JsNumber(1)
    globals.collectionActor.tell(GetState(r), probe.ref)
    val state = probe.expectMsgType[State]
    state.obj \ "name" shouldBe JsString("Hatem AlSum")
    state.obj \ "age" shouldBe JsNumber(30)
    state.obj \ "_rev" shouldBe JsNumber(1)
  }

  it should "reply with AlreadyExists is the object was already created" in {
    val r = R / 'users / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    probe.expectMsgType[CommandError.AlreadyExists]
  }

  it should "validate the object fields in creation against the Model specs" in {
    val r = R / 'cars / globals.quickSand.nextId()
    val userObj = Json.obj("model" -> "Lancer")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val validationFailed = probe.expectMsgType[CommandError.ModelValidationFailed]
    validationFailed.reason.errors(0)._1 shouldBe JsPath() \ "model"
    validationFailed.reason.errors(0)._2(0) shouldBe ValidationError("validation.custom.failed")
  }

  it should "reply with created object and contain default values if not submitted" in {
    val r = R / 'cars / globals.quickSand.nextId()
    val userObj = Json.obj("model" -> "Toyota Corolla")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "production" shouldBe JsNumber(1980)
  }

  it should "reply with created object and contain calculated values" in {
    val r = R / 'cars / globals.quickSand.nextId()
    val userObj = Json.obj("model" -> "Mitsubishi Lancer", "production" -> 2010)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "renewal" shouldBe JsNumber(2020)
  }

  it should "reply with created object and contain CALCULATED values if dependant values not in model" in {
    val r = R / 'cars / globals.quickSand.nextId()
    val userObj = Json.obj("model" -> "Ford Focus")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "renewal" shouldBe JsNumber(1990)
  }

  it should "have the _r as a key in the persisted object" in {
    val r = R / 'users / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    val state = probe.expectMsgType[State]
    state.obj \ "_r" shouldBe JsString(r.toString)
  }

  it should "have the object revision as a part of the state" in {
    val r = R / 'users / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    val state = probe.expectMsgType[State]
    state.obj \ "_rev" shouldBe JsNumber(1)
  }

  it should "validate that model is not found" in {
    val r = R / "images" / globals.quickSand.nextId()
    val postObj = Json.obj("title" -> "First Post", "body" -> "First Body")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, postObj), probe.ref)
    probe.expectMsg(ModelResult.ModelNotFound)
  }

  it should "Create with Neseted SubModels" in {
    val r = R / 'authors / globals.quickSand.nextId()
    val Obj = Json.obj("name" -> "Hatem")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Obj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
    val cr = r / 'posts / globals.quickSand.nextId()
    val postObj = Json.obj("title" -> "First Post", "body" -> "First Body")
    globals.collectionActor.tell(Create(ctx, cr, postObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
  }

  it should "validate the object parent is alive" in {
    val cr = R / 'authors / globals.quickSand.nextId() / 'posts / globals.quickSand.nextId()
    val postObj = Json.obj("title" -> "First Post", "body" -> "First Body")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, cr, postObj), probe.ref)
    probe.expectMsgType[ParentNotFound]
  }

  it should "validate the reference field and create object if reference field is correct" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", true, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val companyR = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj = Json.obj("name" -> "Cloud Niners")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, companyR, companyObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
  }

  it should "return invalid response if reference field refereed to Non exist object" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", true, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val companyR = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val probe = TestProbe()

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[CommandError.ModelValidationFailed]
  }

  it should "create object if reference field is optional and object contain reference field" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", false, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val companyR = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj = Json.obj("name" -> "Cloud Niners")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, companyR, companyObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
  }

  it should "create object if reference field is optional and object doesn't contain reference field" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", false, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val companyR = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj = Json.obj("name" -> "Cloud Niners")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, companyR, companyObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
  }

  "Update" should "get ObjectNotFound for uncreated yet objects" in {
    val r = R / 'users / globals.quickSand.nextId()
    val body = UpdateBody(List.empty)
    val probe = TestProbe()
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsg(CommandError.ObjectNotFound(r))
  }

  it should "get InvalidCommand if the type of update operation isn't supported yet " in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.AddToSet, "age", JsNumber(12))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    val em = probe.expectMsgType[CommandError]
    em.isInstanceOf[CommandError.ValidationFailed] shouldBe true
    val error = em.asInstanceOf[CommandError.ValidationFailed].reason
    error.errors(0)._2(0) shouldEqual ValidationError("error.not.supported")
  }

  it should "update the state correctly when sending a valid Set Operation" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
  }

  it should "fail the data revision is lower than the field last touched revision and operation is Set operation" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    val em = probe.expectMsgType[CommandError.ValidationFailed]
    val error = em.asInstanceOf[CommandError.ValidationFailed].reason
    error.errors(0)._2(0) shouldEqual ValidationError("error.revision.outdated")
  }

  it should "ignore revision if the operation was AddNumber" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "age", JsNumber(2))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
  }

  it should "fail when sending an invalid value for AddNumber Operation" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "age", JsString("a22"))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    val em = probe.expectMsgType[CommandError.ValidationFailed]
    val error = em.asInstanceOf[CommandError.ValidationFailed].reason
    error.errors(0)._2(0) shouldEqual ValidationError("error.value.should.be.number")
  }

  it should "fail when sending AddNumber Operation on a field that hasn't Number type" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "name", JsNumber(1))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    val em = probe.expectMsgType[CommandError.ValidationFailed]
    val error = em.asInstanceOf[CommandError.ValidationFailed].reason
    error.errors(0)._2(0) shouldBe ValidationError("error.non.numeric.field")
  }

  it should "update the state correctly when sending a valid AddNumber Operation" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "age", JsNumber(1))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
  }

  it should "update the calculated Fields too" in {
    val r = R / 'cars / globals.quickSand.nextId()
    val userObj = Json.obj("model" -> "Mitsubishi Lancer", "production" -> 2010, "renewal" -> 2030)
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
    val res = probe.expectMsgType[Result.CreateResult]
    res.body \ "renewal" shouldBe JsNumber(2020)
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "production", JsNumber(2012))))
    globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
    probe.expectMsgType[UpdateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    val state = probe.expectMsgType[State]
    state.obj shouldEqual Json.obj("model" -> "Mitsubishi Lancer", "production" -> 2012, "renewal" -> 2020,
      "_r" -> r, "_rev" -> 2)
  }

  it should "fail if the request revision is greater than the object revision" in {
    val r = R / 'users / globals.quickSand.nextId()
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
    probe.expectMsgType[CreateResult]
    globals.collectionActor.tell(GetState(r), probe.ref)
    probe.expectMsgType[State]
    val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
    globals.collectionActor.tell(Update(ctx, r, 10l, body), probe.ref)
    probe.expectMsg(CommandError.OutdatedRevision)
  }

  it should "update reference field if it refer to exist object" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", true, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val probe = TestProbe()

    val companyR1 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj1 = Json.obj("name" -> "Cloud Niners")
    globals.collectionActor.tell(Create(ctx, companyR1, companyObj1), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR1)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val companyR2 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj2 = Json.obj("name" -> "Really IO")
    globals.collectionActor.tell(Create(ctx, companyR2, companyObj2), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val updateBody = UpdateBody(List(UpdateOp(UpdateCommand.Set, "company", JsString(companyR2.toString))))
    globals.collectionActor.tell(Update(ctx, userR, 1l, updateBody), probe.ref)
    probe.expectMsgType[UpdateResult]

    globals.collectionActor.tell(GetState(userR), probe.ref)
    val state = probe.expectMsgType[State]
    assertResult(companyR2)((state.obj \ "company").as[R])
  }

  it should "fail if you try to set R for non exist object on reference field" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", true, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val probe = TestProbe()

    val companyR1 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj1 = Json.obj("name" -> "Cloud Niners")
    globals.collectionActor.tell(Create(ctx, companyR1, companyObj1), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR1)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val companyR2 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()

    val updateBody = UpdateBody(List(UpdateOp(UpdateCommand.Set, "company", JsString(companyR2.toString))))
    globals.collectionActor.tell(Update(ctx, userR, 1l, updateBody), probe.ref)
    probe.expectMsgType[CommandError.ModelValidationFailed]

    globals.collectionActor.tell(GetState(userR), probe.ref)
    val state = probe.expectMsgType[State]
    assertResult(companyR1)((state.obj \ "company").as[R])
  }

  it should "update reference field to be None if it is optional" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", false, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val probe = TestProbe()

    val companyR1 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj1 = Json.obj("name" -> "Cloud Niners")
    globals.collectionActor.tell(Create(ctx, companyR1, companyObj1), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR1)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val updateBody = UpdateBody(List(UpdateOp(UpdateCommand.Set, "company", JsNull)))
    globals.collectionActor.tell(Update(ctx, userR, 1l, updateBody), probe.ref)
    probe.expectMsgType[UpdateResult]

    globals.collectionActor.tell(GetState(userR), probe.ref)
    val state = probe.expectMsgType[State]
    assertResult(None)((state.obj \ "company").asOpt[R])
  }

  it should "allow only set operation on reference field" in {
    val newUserModel = BaseActorSpec.userModel.copy(
      fields = BaseActorSpec.userModel.fields + ("company" ->
      ReferenceField("company", true, BaseActorSpec.companyModel.r, List("name")))
    )

    val actorProp = TestProbe()
    modelPersistentActor.tell(PersistentModelStore.UpdateModels(List(newUserModel, BaseActorSpec.companyModel)), actorProp.ref)
    modelPersistentActor.tell(PersistentModelStoreFixture.GetState, actorProp.ref)
    val result = actorProp.expectMsgType[List[Model]]
    assert(result.canEqual(List(newUserModel, BaseActorSpec.companyModel)))

    modelRegistryRef.tell(PersistenceUpdate(await = true), actorProp.ref)
    modelRegistryRef.tell(RequestModel.GetModel(BaseActorSpec.userModel.r, actorProp.ref), actorProp.ref)
    actorProp.expectMsg(ModelObject(newUserModel, List(BaseActorSpec.companyModel.r)))

    val probe = TestProbe()

    val companyR1 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()
    val companyObj1 = Json.obj("name" -> "Cloud Niners")
    globals.collectionActor.tell(Create(ctx, companyR1, companyObj1), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val userR = BaseActorSpec.userModel.r / globals.quickSand.nextId()
    val userObj = Json.obj("name" -> "Ahmed", "age" -> 30, "company" -> companyR1)
    globals.collectionActor.tell(Create(ctx, userR, userObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]

    val companyR2 = BaseActorSpec.companyModel.r / globals.quickSand.nextId()

    val updateBody = UpdateBody(List(UpdateOp(UpdateCommand.AddToSet, "company", JsString(companyR2.toString))))
    globals.collectionActor.tell(Update(ctx, userR, 1l, updateBody), probe.ref)
    probe.expectMsgType[CommandError.ValidationFailed]
  }

}

class CollectionTestReallyGlobals(override val config: ReallyConfig, override val actorSystem: ActorSystem) extends TestReallyGlobals(config, actorSystem) {
  override val materializerProps = Props.empty
}
