/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.testkit.{ TestKit, ImplicitSender }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import _root_.io.really.model._
import org.joda.time.DateTime
import org.scalatest._

abstract class BaseActorSpec(conf: ReallyConfig = TestConf.getConfig()) extends TestKit(TestActorSystem(
  "TestActorSystem",
  conf
)) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  import BaseActorSpec._

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val executionContext = system.dispatcher
  implicit val config: ReallyConfig = conf
  val globals = new TestReallyGlobals(config, system)
  val ctx = RequestContext(
    1,
    UserInfo(AuthProvider.Anonymous, R("/_anonymous/1234567"), Application("reallyApp")),
    None, RequestMetadata(None, DateTime.now, "localhost",
      RequestProtocol.WebSockets)
  )

  override def beforeAll() = {
    globals.boot()
    globals.modelRegistryRouter ! PersistentModelStore.AddedModels(List(userModel, companyModel, carModel, authorModel,
      postModel))
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }
}

object BaseActorSpec {
  val userModel = Model(
    R / 'users,
    CollectionMetadata(23),
    Map(
      "name" -> ValueField("name", DataType.RString, None, None, true),
      "age" -> ValueField("age", DataType.RLong, None, None, true)
    ),
    JsHooks(
      None,
      None,
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List.empty
  )
  val companyModel = Model(
    R / 'companies,
    CollectionMetadata(23),
    Map(
      "name" -> ValueField("name", DataType.RString, None, None, true),
      "employees" -> ValueField("employees", DataType.RLong, None, None, true)
    ),
    JsHooks(
      None,
      None,
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List.empty
  )
  val postModel = Model(
    R / 'authors / 'posts,
    CollectionMetadata(23),
    Map(
      "title" -> ValueField("title", DataType.RString, None, None, true),
      "body" -> ValueField("body", DataType.RString, None, None, true)
    ),
    JsHooks(
      None,
      None,
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List.empty
  )
  val authorModel = Model(
    R / 'authors,
    CollectionMetadata(23),
    Map(
      "name" -> ValueField("name", DataType.RString, None, None, true)
    ),
    JsHooks(
      None,
      None,
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List(postModel.r)
  )
  val model = ValueField("model", DataType.RString, Some("""model.split(' ').length >= 2"""), None, true)
  val production = ValueField("production", DataType.RLong, Some("""production >= 1980"""), Some("""1980"""), true)
  val renewal = CalculatedField1("renewal", DataType.RLong,
    """
      |function calculate(production) {
      | return production + 10;
      |}
    """.stripMargin, production)
  val carModel = Model(
    R / 'cars,
    CollectionMetadata(33),
    Map(
      "model" -> model, "production" -> production, "renewal" -> renewal
    ),
    JsHooks(
      Some(""" input.renewal >= 1980 """),
      None,
      None,
      None,
      None,
      None,
      None
    ),
    null,
    List.empty
  )
}

object TestActorSystem {
  def apply(name: String, conf: ReallyConfig): ActorSystem = {
    ActorSystem(name, conf.coreConfig)
  }
}

object TestConf {
  def getConfig(): ReallyConfig = {
    val config = ConfigFactory.load("really-core-test")
    new ReallyConfig(config)
  }
}
