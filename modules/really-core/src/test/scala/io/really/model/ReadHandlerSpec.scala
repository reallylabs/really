/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor.ActorRef
import akka.persistence.Update
import akka.testkit.TestProbe
import io.really.fixture.PersistentModelStoreFixture
import io.really.model.persistent.ModelRegistry.RequestModel.GetModel
import io.really.model.persistent.ModelRegistry.{ ModelResult }
import io.really.model.persistent.PersistentModelStore
import io.really.protocol.{ ReadOpts, GetOpts }
import io.really.Result.{ ReadResult, GetResult }
import io.really._
import _root_.io.really.json.collection.JSONCollection
import _root_.io.really.rql.RQL.{ Term, Operator, TermValue, SimpleQuery, EmptyQuery }
import _root_.io.really.rql.RQLTokens.PaginationToken
import scala.concurrent.duration._

import play.api.libs.json._

import scala.concurrent.Await

class ReadHandlerSpec extends BaseActorSpecWithMongoDB {
  lazy val collection = globals.mongodbConnection.collection[JSONCollection](s"${BaseActorSpec.authorModel.r.head.collection}")

  var modelRouterRef: ActorRef = _
  var modelPersistentActor: ActorRef = _
  val models: List[Model] = List(BaseActorSpec.userModel, BaseActorSpec.authorModel,
    BaseActorSpec.postModel, BaseActorSpec.carModel)

  override def beforeAll() {
    super.beforeAll()
    modelRouterRef = globals.modelRegistry
    modelPersistentActor = globals.persistentModelStore

    modelPersistentActor ! PersistentModelStore.UpdateModels(models)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef ! Update(await = true)
    modelRouterRef ! GetModel(BaseActorSpec.userModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.userModel, List.empty))
  }

  "Get Handler" should "handle get request if object was exist" in {
    val r = R / 'users / 111
    globals.readHandler ! Request.Get(ctx, r, GetOpts())
    expectMsg(CommandError.ObjectNotFound(r))
  }

  it should "succeed if the request was exist and return all model fields if the client sent empty fields" in {
    val r = R / 'cars / 9999
    val userObj = Json.obj("model" -> "Honda Civic", "production" -> 2010, "_rev" -> 1, "_r" -> r, "_id" -> 9999)
    val collection = globals.mongodbConnection.collection[JSONCollection]("cars")
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true
    globals.readHandler ! Request.Get(ctx, r, GetOpts(Set("model", "production")))
    val ret = expectMsgType[GetResult]
    ret.body shouldEqual userObj - ("_id")
  }

  it should "succeed if the request was exist and return only the requested fields" in {
    val r = R / 'cars / 9998
    val userObj = Json.obj("model" -> "Toyota Avalon", "production" -> 2012, "_rev" -> 1, "_r" -> r, "_id" -> 9998)
    val collection = globals.mongodbConnection.collection[JSONCollection]("cars")
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true
    globals.readHandler ! Request.Get(ctx, r, GetOpts(Set("model")))
    val ret = expectMsgType[GetResult]
    ret.body shouldEqual Json.obj("model" -> "Toyota Avalon", "_rev" -> 1.0, "_r" -> "/cars/9998/")
  }

  it should "fail if the provided r didn't match any model" in {
    val r = R / 'test / 111
    globals.readHandler ! Request.Get(ctx, r, GetOpts())
    expectMsg(CommandError.ModelNotFound)
  }

  "Read Handler" should "fail if the provided r didn't match any model" in {
    val r = R / 'test
    val cmdOpts = ReadOpts(
      Set.empty,
      EmptyQuery,
      5,
      false,
      None,
      0,
      false,
      false
    )
    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    expectMsg(CommandError.ModelNotFound)
  }

  it should "succeed if the provided data was valid" in {
    val r = R / 'authors / 11 / 'posts
    val cmdOpts = ReadOpts(
      Set.empty,
      EmptyQuery,
      5,
      false,
      None,
      0,
      false,
      false
    )
    val postObj = Json.obj("title" -> "The post title", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 111,
      "_parent0" -> "authors/11/", "_id" -> 111)
    val collection = globals.mongodbConnection.collection[JSONCollection]("posts_authors")
    val result = Await.result(collection.save(postObj), 5.second)
    result.ok shouldBe true

    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (1)
    ret.body.items(0).body - "author" shouldEqual ((postObj - "_id") - "_parent0")
    ret.subscription shouldBe None
    ret.body.totalResults shouldBe None
    ret.body.tokens.get.nextToken shouldEqual PaginationToken(111, 1)
    ret.body.tokens.get.prevToken shouldEqual PaginationToken(111, 0)
  }

  it should "succeed and include total count if the totalResults option equals true" in {
    val r = R / 'authors / 12 / 'posts
    val cmdOpts = ReadOpts(
      Set.empty,
      EmptyQuery,
      1,
      false,
      None,
      0,
      true,
      false
    )
    val collection = globals.mongodbConnection.collection[JSONCollection]("posts_authors")
    val userObj = Json.obj("title" -> "The post title", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 111,
      "_parent0" -> "authors/12/", "_id" -> 111)
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true

    val userObj2 = Json.obj("title" -> "The post title", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 112,
      "_parent0" -> "authors/12/", "_id" -> 112)
    val result2 = Await.result(collection.save(userObj2), 5.second)
    result2.ok shouldBe true

    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (1)
    ret.body.totalResults shouldBe Some(2)
  }

  it should "succeed and return only selected fields" in {
    val r = R / 'authors / 12 / 'posts
    val cmdOpts = ReadOpts(
      Set("title"),
      EmptyQuery,
      10,
      false,
      None,
      0,
      true,
      false
    )
    val collection = globals.mongodbConnection.collection[JSONCollection]("posts_authors")
    val userObj = Json.obj("title" -> "The post title", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 111,
      "_parent0" -> "authors/12/", "_id" -> 111)
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true

    val userObj2 = Json.obj(
      "_deleted" -> true,
      "_rev" -> 1, "_r" -> r / 111,
      "_parent0" -> "authors/12/", "_id" -> 111
    )
    val result2 = Await.result(collection.save(userObj2), 5.second)
    result2.ok shouldBe true

    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (1)
    ret.body.totalResults shouldEqual Some(2) //todo: should be 1 if RIO-108 is closed
    ret.body.items(0).body shouldEqual Json.obj("_r" -> "/authors/12/posts/112/", "_rev" -> 1, "title" -> "The post title")
  }

  it should "return object gone if trying to get a deleted object" in {
    val r = R / 'cars / 2015
    val userObj = Json.obj("_rev" -> 1, "_r" -> r, "_id" -> 2015, "_deleted" -> true)
    val collection = globals.mongodbConnection.collection[JSONCollection]("cars")
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true
    globals.readHandler ! Request.Get(ctx, r, GetOpts(Set("model", "production")))
    expectMsg(CommandError.ObjectGone(r))

  }

  it should "succeed and filter data according to query read option" in {
    val r = R / 'authors / 13 / 'posts
    val query = SimpleQuery(Term("title"), Operator.Eq, TermValue(JsString("The post title")))
    val cmdOpts = ReadOpts(
      Set.empty,
      query,
      10,
      false,
      None,
      0,
      true,
      false
    )
    val collection = globals.mongodbConnection.collection[JSONCollection]("posts_authors")
    val userObj = Json.obj("title" -> "The post title", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 111,
      "_parent0" -> "authors/13/", "_id" -> 111)
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true

    val userObj2 = Json.obj("title" -> "Welcome to Scala", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 112,
      "_parent0" -> "authors/13/", "_id" -> 112)
    val result2 = Await.result(collection.save(userObj2), 5.second)
    result2.ok shouldBe true

    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (1)
    ret.body.items(0).body - "author" shouldEqual Json.obj(
      "title" -> "The post title",
      "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 111
    )
    ret.body.totalResults shouldBe Some(1)
  }

  it should "return calculated fields if requested" in {
    val r = R / 'cars
    val query = SimpleQuery(Term("model"), Operator.Eq, TermValue(JsString("KIA")))
    val cmdOpts = ReadOpts(
      Set("model", "renewal"),
      query,
      10,
      false,
      None,
      0,
      true,
      false
    )
    val collection = globals.mongodbConnection.collection[JSONCollection]("cars")
    val carObj = Json.obj("model" -> "KIA", "production" -> 2000,
      "_rev" -> 1, "_r" -> r / 1000, "_id" -> 1000)
    val result = Await.result(collection.save(carObj), 5.second)
    result.ok shouldBe true

    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (1)
    ret.body.items(0).body shouldEqual Json.obj("model" -> "KIA", "renewal" -> 2010,
      "_rev" -> 1, "_r" -> R("/cars/1000/"))

  }

  it should "handle querying data with ['Gt', 'Lt', 'Gte', 'Lte', 'Between'] operators" in {
    val r = R / 'users
    val collection = globals.mongodbConnection.collection[JSONCollection]("users")
    val userObj = Json.obj("name" -> "Augustine", "age" -> 20,
      "_rev" -> 1, "_r" -> r / 1000, "_id" -> 1000)
    val result = Await.result(collection.save(userObj), 5.second)
    result.ok shouldBe true

    val userObj2 = Json.obj("name" -> "Lee minho", "age" -> 28,
      "_rev" -> 1, "_r" -> r / 1001, "_id" -> 1001)
    val result2 = Await.result(collection.save(userObj2), 5.second)
    result2.ok shouldBe true

    val userObj3 = Json.obj("name" -> "Joo Woo", "age" -> 10,
      "_rev" -> 1, "_r" -> r / 1002, "_id" -> 1002)
    val result3 = Await.result(collection.save(userObj3), 5.second)
    result3.ok shouldBe true

    val userObj4 = Json.obj("name" -> "Sara", "age" -> 22,
      "_rev" -> 1, "_r" -> r / 1003, "_id" -> 1003)
    val result4 = Await.result(collection.save(userObj4), 5.second)
    result4.ok shouldBe true

    val query = SimpleQuery(Term("age"), Operator.Gt, TermValue(JsNumber(20)))
    val cmdOpts = ReadOpts(
      Set("name", "age"),
      query,
      10,
      false,
      None,
      0,
      true,
      false
    )
    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (2)
    ret.body.items(0).body shouldEqual Json.obj("name" -> "Sara", "age" -> 22, "_r" -> "/users/1003/", "_rev" -> 1)
    ret.body.items(1).body shouldEqual Json.obj("name" -> "Lee minho", "age" -> 28, "_r" -> "/users/1001/", "_rev" -> 1)

    val query2 = SimpleQuery(Term("age"), Operator.Lt, TermValue(JsNumber(20)))
    val cmdOpts2 = ReadOpts(
      Set("name", "age"),
      query2,
      10,
      false,
      None,
      0,
      true,
      false
    )
    globals.readHandler ! Request.Read(ctx, r, cmdOpts2, TestProbe().ref)
    val ret2 = expectMsgType[ReadResult]
    ret2.body.items.size shouldEqual (1)
    ret2.body.items(0).body shouldEqual Json.obj("name" -> "Joo Woo", "age" -> 10, "_r" -> "/users/1002/", "_rev" -> 1)

    val query3 = SimpleQuery(Term("age"), Operator.Gte, TermValue(JsNumber(20)))
    val cmdOpts3 = ReadOpts(
      Set("name", "age"),
      query3,
      10,
      false,
      None,
      0,
      true,
      false
    )
    globals.readHandler ! Request.Read(ctx, r, cmdOpts3, TestProbe().ref)
    val ret3 = expectMsgType[ReadResult]
    ret3.body.items.size shouldEqual (3)
    ret3.body.items(0).body shouldEqual Json.obj("name" -> "Sara", "age" -> 22, "_r" -> "/users/1003/", "_rev" -> 1)
    ret3.body.items(1).body shouldEqual Json.obj("name" -> "Lee minho", "age" -> 28, "_r" -> "/users/1001/", "_rev" -> 1)
    ret3.body.items(2).body shouldEqual Json.obj("name" -> "Augustine", "age" -> 20, "_r" -> "/users/1000/", "_rev" -> 1)

    val query4 = SimpleQuery(Term("age"), Operator.Lte, TermValue(JsNumber(20)))
    val cmdOpts4 = ReadOpts(
      Set("name", "age"),
      query4,
      10,
      false,
      None,
      0,
      true,
      false
    )
    globals.readHandler ! Request.Read(ctx, r, cmdOpts4, TestProbe().ref)
    val ret4 = expectMsgType[ReadResult]
    ret4.body.items.size shouldEqual (2)
    ret4.body.items(0).body shouldEqual Json.obj("name" -> "Joo Woo", "age" -> 10, "_r" -> "/users/1002/", "_rev" -> 1)
    ret4.body.items(1).body shouldEqual Json.obj("name" -> "Augustine", "age" -> 20, "_r" -> "/users/1000/", "_rev" -> 1)

    val query5 = SimpleQuery(Term("age"), Operator.Between, TermValue(JsArray(Seq(JsNumber(20), JsNumber(25)))))
    val cmdOpts5 = ReadOpts(
      Set("name", "age"),
      query5,
      10,
      false,
      None,
      0,
      true,
      false
    )
    globals.readHandler ! Request.Read(ctx, r, cmdOpts5, TestProbe().ref)
    val ret5 = expectMsgType[ReadResult]
    ret5.body.items.size shouldEqual (2)
    ret5.body.items(0).body shouldEqual Json.obj("name" -> "Sara", "age" -> 22, "_r" -> "/users/1003/", "_rev" -> 1)
  }

  it should "be able to paginate data with 'paginationToken'" in {
    val r = R / 'authors / 15 / 'posts
    val cmdOpts = ReadOpts(
      Set.empty,
      EmptyQuery,
      2,
      false,
      None,
      0,
      false,
      false
    )
    //Add Dummy data
    val collection = globals.mongodbConnection.collection[JSONCollection]("posts_authors")

    val postObj = Json.obj("title" -> "The post title", "body" -> "The post body",
      "_rev" -> 1, "_r" -> r / 111,
      "_parent0" -> "authors/15/", "_id" -> 111)
    val result = Await.result(collection.save(postObj), 5.second)
    result.ok shouldBe true

    val postObj2 = Json.obj("title" -> "The post title2", "body" -> "The post body2",
      "_rev" -> 1, "_r" -> r / 112,
      "_parent0" -> "authors/15/", "_id" -> 112)
    val result2 = Await.result(collection.save(postObj2), 5.second)
    result2.ok shouldBe true

    val postObj3 = Json.obj("title" -> "The post title3", "body" -> "The post body3",
      "_rev" -> 1, "_r" -> r / 113,
      "_parent0" -> "authors/15/", "_id" -> 113)
    val result3 = Await.result(collection.save(postObj3), 5.second)
    result3.ok shouldBe true

    val postObj4 = Json.obj("title" -> "The post title4", "body" -> "The post body4",
      "_rev" -> 1, "_r" -> r / 114,
      "_parent0" -> "authors/15/", "_id" -> 114)
    val result4 = Await.result(collection.save(postObj4), 5.second)
    result4.ok shouldBe true

    val postObj5 = Json.obj("title" -> "The post title5", "body" -> "The post body5",
      "_rev" -> 1, "_r" -> r / 115,
      "_parent0" -> "authors/15/", "_id" -> 115)
    val result5 = Await.result(collection.save(postObj5), 5.second)
    result5.ok shouldBe true

    globals.readHandler ! Request.Read(ctx, r, cmdOpts, TestProbe().ref)
    val ret = expectMsgType[ReadResult]
    ret.body.items.size shouldEqual (2)
    ret.body.totalResults shouldBe None
    ret.body.tokens.get.nextToken shouldEqual PaginationToken(114, 1)
    ret.body.tokens.get.prevToken shouldEqual PaginationToken(115, 0)

    globals.readHandler ! Request.Read(ctx, r, cmdOpts.copy(ascending = true), TestProbe().ref)
    val ret2 = expectMsgType[ReadResult]
    ret2.body.items.size shouldEqual (2)
    ret2.body.totalResults shouldBe None
    ret2.body.tokens.get.nextToken shouldEqual PaginationToken(112, 1)
    ret2.body.tokens.get.prevToken shouldEqual PaginationToken(111, 0)

    val readOpts = cmdOpts.copy(paginationToken = Some(ret.body.tokens.get.nextToken))
    globals.readHandler ! Request.Read(ctx, r, readOpts, TestProbe().ref)
    val ret3 = expectMsgType[ReadResult]
    ret3.body.items.size shouldEqual (2)
    ret3.body.tokens.get.nextToken shouldEqual PaginationToken(112, 1)
    ret3.body.tokens.get.prevToken shouldEqual PaginationToken(113, 0)

    globals.readHandler ! Request.Read(ctx, r, readOpts.copy(ascending = true), TestProbe().ref)
    val ret4 = expectMsgType[ReadResult]
    ret4.body.items.size shouldEqual (2)
    ret4.body.totalResults shouldBe None
    ret4.body.tokens.get.nextToken shouldEqual PaginationToken(112, 1)
    ret4.body.tokens.get.prevToken shouldEqual PaginationToken(111, 0)
  }

}