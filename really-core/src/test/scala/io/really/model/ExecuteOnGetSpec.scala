/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really._
import org.joda.time.DateTime
import org.scalatest.{ Matchers, FlatSpec }
import play.api.libs.json._

class ExecuteOnGetSpec extends BaseActorSpec {

  val context = RequestContext(
    tag = 1,
    auth = UserInfo(AuthProvider.Anonymous, "1234567", None),
    meta = RequestMetadata(
      traceId = None,
      when = DateTime.now,
      host = "localhost",
      protocol = RequestProtocol.WebSockets
    )
  )

  "PreGet JsHook" should "return FieldProtectionPlan contains list of hidden fields" in {
    val onGetJs: JsScript =
      """
        |if(auth.userR != obj._r){
        |hide("age");
        |}
      """.stripMargin

    val input = Json.obj(
      "_r" -> "/users/121333",
      "name" -> "Ahmed",
      "age" -> 20
    )

    val model = BaseActorSpec.userModel.copy(jsHooks = JsHooks(None, preGet = Some(onGetJs), None, None, None, None, None))

    model.executeOnGet(context, globals, input) should be(Right(FieldProtectionPlan(Set("age"))))
  }

  it should "return list of hidden fields and escape fields don't contains on Model" in {
    val onGetJs: JsScript =
      """
        |if(auth.userR != obj._r){
        |hide("age", "FName");
        |}
      """.stripMargin

    val input = Json.obj(
      "_r" -> "/users/121333",
      "name" -> "Ahmed",
      "age" -> 20
    )

    val model = BaseActorSpec.userModel.copy(jsHooks = JsHooks(None, preGet = Some(onGetJs), None, None, None, None, None))

    model.executeOnGet(context, globals, input) should be(Right(FieldProtectionPlan(Set("age"))))
  }

  it should "return Terminated if you write invalid javascript code" in {
    val onGetJs: JsScript =
      """
        |if(auth.userR != obj._r){
        |hide(22);
        |}
      """.stripMargin

    val input = Json.obj(
      "_r" -> "/users/121333",
      "name" -> "Ahmed",
      "age" -> 20
    )

    val model = BaseActorSpec.userModel.copy(jsHooks = JsHooks(None, preGet = Some(onGetJs), None, None, None, None, None))

    model.executeOnGet(context, globals, input) should be(Left(ModelHookStatus.Terminated(9000, "preGet contain hide function with invalid args.")))
  }

}