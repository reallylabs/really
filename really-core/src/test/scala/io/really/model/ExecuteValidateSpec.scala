/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really._
import org.joda.time.DateTime
import org.scalatest._
import play.api.libs.json.{ JsNumber, JsString, Json, JsObject }
import _root_.io.really.model.ModelHookStatus.{ Succeeded, Terminated }

class ExecuteValidateSpec extends BaseActorSpec {
  val r = R / "users"
  val collMeta: CollectionMetadata = CollectionMetadata(1L)
  val nameField = ValueField("name", DataType.RString, None, None, true)
  val ageField = ValueField("age", DataType.RLong, None, None, true)
  val fields: Map[FieldKey, Field[_]] = Map(
    "name" -> nameField,
    "age" -> ageField
  )
  val validationScript: JsScript =
    """
      |var x = 14;
      |if(x > 18) {
      |cancel(x, "Over Age!")
      |}
    """.stripMargin
  val jsHooks: JsHooks = JsHooks(onValidate = Some(validationScript), None, None, None, None, None, None)

  val migrationPlan: MigrationPlan = MigrationPlan(Map.empty)

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

  val userModel = new Model(r, collMeta, fields, jsHooks, migrationPlan, List.empty)
  val input: JsObject = Json.obj(
    "name" -> JsString("Ahmed"),
    "age" -> JsNumber(23),
    "address" -> Json.obj("streetName" -> JsString("BS"), "block" -> JsNumber(23))
  )

  "Validate JsHooks" should "pass if the JS validation script ended without calling cancel()" in {

    userModel.executeValidate(context, globals, input) should be(Succeeded)

  }
  it should "return Terminated object if cancel() was called with error code and message" in {
    val validationScript: JsScript =
      """
        |var x = 23;
        |if (x > 18) {
        | cancel(401, "Over Age!");
        | }
      """.stripMargin
    val jsHooks: JsHooks = JsHooks(onValidate = Some(validationScript), None, None, None, None, None, None)
    val userModel = new Model(r, collMeta, fields, jsHooks, migrationPlan, List.empty)
    userModel.executeValidate(context, globals, input) should be(Terminated(401, "Over Age!"))
  }
  it should "deals with the input seamlessly as JSON" in {
    val validationScript: JsScript =
      """
        |var streetName = input.address.streetName;
        |var block = input.address.block;
        |var address = "Address is: " + streetName + ", block: " + block;
        |print(address);
      """.stripMargin
    val jsHooks: JsHooks = JsHooks(onValidate = Some(validationScript), None, None, None, None, None, None)
    val userModel = new Model(r, collMeta, fields, jsHooks, migrationPlan, List.empty)
    userModel.executeValidate(context, globals, input) should be(Succeeded)

  }

  it should "pass if the preUpdate script ended without calling cancel()" in {

    val preUpdateScript: JsScript =
      """
        |var before_str = "Name is: " + before.name + ", age : " + before.age;
        |var after_str = "Name is: " + after.name + ", age : " + after.age;
        |print(before_str)
        |print(after_str)
        |for(var i =0 ; i < fields.length ;i++){
        |print("changed field is : "+fields[i]);
        |}
      """.stripMargin

    val after: JsObject = Json.obj(
      "name" -> JsString("Hatem"),
      "age" -> JsNumber(30),
      "address" -> Json.obj("streetName" -> JsString("N"), "block" -> JsNumber(300))
    )

    val fieldsChanged = fields.map(f => f._1).toList
    val jsHooks: JsHooks = JsHooks(None, None, preUpdate = Some(preUpdateScript), None, None, None, None)
    val userModel = new Model(r, collMeta, fields, jsHooks, migrationPlan, List.empty)
    userModel.executeValidate(context, globals, input) should be(Succeeded)
    userModel.executePreUpdate(context, globals, input, after, fieldsChanged) should be(Succeeded)

  }

  it should "return Terminated object if preUpdate cancel() was called" in {

    val preUpdateScript: JsScript =
      """
        |print(after.age)
        |if(after.age > 30){
        | cancel(401   , "too Old")
        |}
      """.stripMargin

    val after: JsObject = Json.obj(
      "name" -> JsString("Hatem"),
      "age" -> JsNumber(50)
    )

    val fieldsChanged = fields.map(f => f._1).toList
    val jsHooks: JsHooks = JsHooks(None, None, preUpdate = Some(preUpdateScript), None, None, None, None)
    val userModel = new Model(r, collMeta, fields, jsHooks, migrationPlan, List.empty)
    userModel.executeValidate(context, globals, input) should be(Succeeded)
    userModel.executePreUpdate(context, globals, input, after, fieldsChanged) should be(Terminated(401, "too Old"))

  }

}
