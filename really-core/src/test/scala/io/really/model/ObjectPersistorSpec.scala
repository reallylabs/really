package io.really.model

import akka.actor.{Terminated, ReceiveTimeout}
import akka.testkit.{EventFilter, TestActorRef}
import io.really.{R, BaseActorSpec}
import play.api.libs.json.{Json, JsString}

class ObjectPersistorSpec(testActor: String) extends BaseActorSpec(testActor) {
  def this() = this("ObjectPersistorTest")

  val model = Model(R / 'users, CollectionMetadata(1), Map("name" -> ValueField("name", DataType.RString, None, None,
    true)), JsHooks(Some( """input.name + ' Bey' """),
    None,
    None,
    None,
    None,
    None,
    None), null)
  
  "ObjectPersistor" should "have the persistenceID as the object's R" in {
    val r = R / 'users / 1
    val userObj = Json.obj("key" -> "value")
    val objectPersistorRef = TestActorRef[ObjectPersistor](ObjectPersistor.props(globals, r, model))
    val objectPersistor = objectPersistorRef.underlyingActor
    objectPersistor.persistenceId shouldBe r.toString
  }

  it should "have the companion R represents an object not a collection" in {
    val r = R / 'users
    intercept[IllegalArgumentException] {
      system.actorOf(ObjectPersistor.props(globals, r, model))
    }
  }

  it should "update the state correctly when Create Command is sent for first time" in {
    val model = Model(R / 'users, CollectionMetadata(33), Map("name" -> ValueField("name", DataType.RString, None,
      None, true)),
      JsHooks(Some( """input.name + ' Bey' """),
      None,
      None,
      None,
      None,
      None,
      None), null)
    val r = R / 'users / 2
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Ahmed")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ObjectCreated]
    objectPersistorRef ! GetState
    val state = expectMsgType[State]
    state.obj \ "name" shouldBe JsString("Ahmed")
    state.modelVersion shouldBe 33
  }

  it should "reply with AlreadyExists is the object was already created" in {
    val r = R / 'users / 3
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Ahmed")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ObjectCreated]
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[AlreadyExists]
  }

  it should " accept handle the ReceiveTimeout message" in {
    val r = R / 'users / 4
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Ahmed")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ObjectCreated]
    val message = (s"Persistor $r is idle, requesting to be terminated")
    EventFilter.debug(message = message, occurrences = 1) intercept {
      objectPersistorRef ! ReceiveTimeout
    }
  }

  it should "recover correctly to last state before being stopped" in {
    val r = R / 'users / 5
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Neo, The One!")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ObjectCreated]
    watch(objectPersistorRef)
    system.stop(objectPersistorRef)
    expectMsgType[Terminated]
    val objectPersistorRefNew = system.actorOf(ObjectPersistor.props(globals, r, model))
    objectPersistorRefNew ! GetState
    val state = expectMsgType[State]
    state.obj \ "name" shouldBe JsString("Neo, The One!")
  }

  it should "reply with ValidationFailed if the Validation hook did not pass" in {
    val model = Model(R / 'users, CollectionMetadata(33), Map("name" -> ValueField("name", DataType.RString, None, None,
      true)),
      JsHooks(Some( """cancel(400, 'Forbidden!')"""),
      None,
      None,
      None,
      None,
      None,
      None), null)
    val r = R / 'users / 6
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Ahmed")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ValidationFailed]
  }

  it should "reply with ValidationFailed if the object data did not pass the model specs validation" in {
    //TODO Should be done when the model validation is implemented
  }

  it should "have the _r as a key in the persisted object" in {
    val r = R / 'users / 7
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Ahmed")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ObjectCreated]
    objectPersistorRef ! GetState
    val state = expectMsgType[State]
    state.obj \ "name" shouldBe JsString("Ahmed")
    state.obj \ "_r" shouldBe JsString(r.toString)
  }

  it should "have the model version as a part of the state" in {
    val model = Model(R / 'users, CollectionMetadata(23), Map("name" -> ValueField("name", DataType.RString, None, None,
      true)),
      JsHooks(Some(""),
      None,
      None,
      None,
      None,
      None,
      None), null)
    val r = R / 'users / 8
    val objectPersistorRef = system.actorOf(ObjectPersistor.props(globals, r, model))
    val userObj = Json.obj("name" -> "Ahmed")
    objectPersistorRef ! Create(null, userObj)
    expectMsgType[ObjectCreated]
    objectPersistorRef ! GetState
    val state = expectMsgType[State]
    state.modelVersion shouldBe 23
  }
}