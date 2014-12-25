/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.rql

import io.really.rql.RQLTokens.PaginationToken
import org.scalatest.{ Matchers, FlatSpec }
import play.api.data.validation.ValidationError
import play.api.libs.json._

class RQLTokensSpec extends FlatSpec with Matchers {

  "PaginationToken" should "fail when trying to generate token with direction isn't equal 0 or 1" in {
    intercept[IllegalArgumentException] {
      PaginationToken(12345678908765432l, 4)
    }
  }

  it should "be readable from JsValue" in {
    JsString("111111113333:0").validate(PaginationToken.reads).get should equal(PaginationToken(111111113333l, 0))
  }

  it should "Write QueryToken to JsValue" in {
    val token = PaginationToken(2133121331, 0)
    Json.toJson(token) should equal(JsString("2133121331:0"))
  }

  "PaginationToken Parsing" should "pass if the string is a valid one" in {
    val tokenResult = PaginationToken.parse("1231231234532:1")
    tokenResult should equal(JsSuccess(PaginationToken(1231231234532l, 1)))
  }

  it should "fail if the string hasn't direction as integer" in {
    val tokenResult = PaginationToken.parse("1231231234532:X")
    tokenResult.isInstanceOf[JsError] shouldBe true
    tokenResult.asInstanceOf[JsError].errors.contains(Seq(JsPath() -> Seq(ValidationError("error.invalid.token.format"))))
  }

}