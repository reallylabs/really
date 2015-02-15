/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import _root_.io.really.rql.RQL.EmptyQuery
import _root_.io.really.protocol.ReadOpts
import akka.testkit.TestProbe

class RequestCreationSpec extends BaseActorSpec {

  "Request Creation" should "return error if command is Get and r is collection" in {
    val r = R / "boards"
    val exception = intercept[IllegalArgumentException] {
      Request.Get(ctx, r, null)
    }
    exception.getMessage.contains("r should be represent object") shouldEqual true
  }

  it should "return error if command is Read and r is object" in {
    val r = R / "boards" / 3
    val exception = intercept[IllegalArgumentException] {
      Request.Read(ctx, r, ReadOpts(Set.empty, EmptyQuery, 10, true, None, 0, false, false), TestProbe().ref)
    }
    exception.getMessage.contains("r should be represent collection") shouldEqual true
  }

  it should "return error if command is Update and r is collection" in {
    val r = R / "boards"
    val exception = intercept[IllegalArgumentException] {
      Request.Update(ctx, r, 12, null)
    }
    exception.getMessage.contains("r should be represent object") shouldEqual true
  }

  it should "return error if command is Delete and r is collection" in {
    val r = R / "boards"
    val exception = intercept[IllegalArgumentException] {
      Request.Delete(ctx, r)
    }
    exception.getMessage.contains("r should be represent object") shouldEqual true
  }
}
