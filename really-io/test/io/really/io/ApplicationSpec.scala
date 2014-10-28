/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io

import org.scalatest._
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

class ApplicationSpec extends PlaySpec with BeforeAndAfterAll {

  "Application" must {
    "send 404 on a bad request" in new WithApplication {
      assert(route(FakeRequest(GET, "/boum")) == None)
    }

    "render the index page" in new WithApplication {
      val home = route(FakeRequest(GET, "/")).get

      assert(status(home) == OK)
      contentType(home) mustBe Some("text/plain")
      contentAsString(home) must include ("Howdy!")
    }
  }
}
