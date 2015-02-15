/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.token.generator

import io.really.jwt.{ JWTResult, JWT }
import org.joda.time.DateTime
import org.scalatest.{ ShouldMatchers, FlatSpec }
import play.api.libs.json.Json

class JWTGeneratorSpec extends FlatSpec with ShouldMatchers {
  val secretKey = "$%^&*)(*&^ddwefdrrewq34543!SDfgg"

  "Really Token" should "return encoded anonymous token when calling `createAnonymousToken`" in {
    val anonyToken = JWTGenerator(secretKey).createAnonymousToken("12234455")
    val JWTResult.JWT(_, payload) = JWT.decode(anonyToken, Some(secretKey))
    payload shouldEqual Json.obj("uid" -> "12234455", "authType" -> "anonymous", "expires" -> 0, "data" -> Json.obj())
  }

  it should "return auth token when sending a valid data" in {
    val authToken = JWTGenerator(secretKey).createAuthToken("/users/12345678987654", AuthType.Password, "12234455")
    val JWTResult.JWT(_, payload) = JWT.decode(authToken, Some(secretKey))
    payload shouldEqual Json.obj("_r" -> "/users/12345678987654", "uid" -> "12234455",
      "authType" -> "password", "expires" -> 0, "data" -> Json.obj())
  }

  "createAnonymousToken" should "set the expires timestamp if the expire data was provided" in {
    val exp = DateTime.now().plusDays(7)
    val anonyToken = JWTGenerator(secretKey).createAnonymousToken("12234455", expires = Some(exp))
    val JWTResult.JWT(_, payload) = JWT.decode(anonyToken, Some(secretKey))
    payload shouldEqual Json.obj("uid" -> "12234455", "authType" -> "anonymous", "expires" -> exp.getMillis, "data" -> Json.obj())
  }

  "createAuthToken" should "set the expires timestamp if the expire data was provided" in {
    val exp = DateTime.now().plusDays(7)
    val authToken = JWTGenerator(secretKey).createAuthToken(
      "/users/12345678987654",
      AuthType.Password, "12234455", expires = Some(exp)
    )
    val JWTResult.JWT(_, payload) = JWT.decode(authToken, Some(secretKey))
    payload shouldEqual Json.obj("_r" -> "/users/12345678987654", "uid" -> "12234455",
      "authType" -> "password", "expires" -> exp.getMillis, "data" -> Json.obj())
  }
}