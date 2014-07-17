package io.really

import org.scalatest._
import spray.testkit.ScalatestRouteTest
import spray.http._
import StatusCodes._

class HelloWorldRoutesSpec extends FlatSpec with Matchers with ScalatestRouteTest with HelloWorldRoutes {
  def actorRefFactory = system

  "HelloWorldRoutes" should "return a greeting for GET requests to the root path" in {
    Get() ~> myRoute ~> check {
      responseAs[String] should include("Say hello")
    }
  }

  it should "leave GET requests to other paths unhandled" in {
    Get("/kermit") ~> myRoute ~> check {
      assert(handled == false)
    }
  }

  it should "return a MethodNotAllowed error for PUT requests to the root path" in {
    Put() ~> sealRoute(myRoute) ~> check {
      assert(status == MethodNotAllowed)
      assert(responseAs[String] == "HTTP method not allowed, supported methods: GET")
    }
  }

}
