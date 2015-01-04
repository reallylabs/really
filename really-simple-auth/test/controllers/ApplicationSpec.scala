// /**
//  * Copyright (C) 2014-2015 Really Inc. <http://really.io>
//  */

// todo: This test is commented until scalatestplus-play releases a version that supports play 2.4.X

// package controllers

// import io.really.jwt.{ JWTResult, JWT }
// import org.joda.time.DateTime
// import org.scalatest._
// import play.api.{ Configuration, Play }
// import play.api.libs.json._
// import play.api.test._
// import play.api.test.Helpers._
// import org.scalatestplus.play._

// class ApplicationSpec extends PlaySpec with OneAppPerSuite with BeforeAndAfterAll {

//   implicit override lazy val app: FakeApplication =
//     FakeApplication(
//       additionalConfiguration = Map(
//         "really.secret" -> "UpB]wpa`o2SW<]<A[@LvL^IR=?16]B9beU[qiD",
//         "really.token.anonymous.duration" -> "1 day"
//       )
//     )

//   "Simple Auth Application" must {

//     "return new anonymous token in case of sending anonymous login request" in {
//       val anonymousLogin = route(FakeRequest(POST, "/anonymous/login/")).get
//       assert(status(anonymousLogin) == OK)
//       contentType(anonymousLogin) mustBe Some("application/json")
//       (contentAsJson(anonymousLogin) \ "accessToken").asOpt[String].isDefined mustBe true
//       (contentAsJson(anonymousLogin) \ "authType").asOpt[String] mustEqual Some("anonymous")
//     }

//     "return new anonymous token with data encoded in case of sending anonymous login request with data" in {
//       val reallyConfig = Play.current.configuration.getConfig("really").getOrElse(Configuration.empty)

//       val dataObj = Json.obj("nickname" -> "Amal")
//       val anonymousLogin = route(FakeRequest(POST, "/anonymous/login/"), dataObj).get
//       assert(status(anonymousLogin) == OK)
//       contentType(anonymousLogin) mustBe Some("application/json")
//       val token = (contentAsJson(anonymousLogin) \ "accessToken").asOpt[String]
//       token.isDefined mustBe true
//       (contentAsJson(anonymousLogin) \ "authType").asOpt[String] mustEqual Some("anonymous")
//       val JWTResult.JWT(_, payload) = JWT.decode(token.get, Some(reallyConfig.getString("secret").get))
//       (payload \ "data").as[JsObject] mustEqual dataObj
//     }

//     "add expire to the created anonymous tokens" in {
//       val reallyConfig = Play.current.configuration.getConfig("really").getOrElse(Configuration.empty)

//       val anonymousLogin = route(FakeRequest(POST, "/anonymous/login/")).get
//       assert(status(anonymousLogin) == OK)
//       contentType(anonymousLogin) mustBe Some("application/json")
//       val token = (contentAsJson(anonymousLogin) \ "accessToken").as[String]
//       val JWTResult.JWT(_, payload) = JWT.decode(token, Some(reallyConfig.getString("secret").get))
//       (payload \ "expires").as[Long] > DateTime.now().getMillis mustBe true
//     }
//   }
// }