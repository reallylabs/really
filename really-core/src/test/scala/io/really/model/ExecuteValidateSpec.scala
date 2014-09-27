package io.really.model

import io.really.R
import org.joda.time.DateTime
import org.scalatest._
import io.really.{AuthInfo, RequestContext, RequestMetadata, RequestProtocol}
import play.api.libs.json.{JsNumber, JsString, Json, JsObject}
import io.really.model.ModelHookStatus.{Succeeded, Terminated}

class ExecuteValidateSpec extends FlatSpec with Matchers {
  val r = R / "users"
  val collMeta: CollectionMetadata = CollectionMetadata(1L)
  val nameField = ValueField("name", DataType.RString, None, None, true)
  val ageField = ValueField("age", DataType.RLong, None, None, true)
  val fields: Map[FieldKey, Field[_]] = Map("name" -> nameField,
    "age" -> ageField)
  val validationScript: JsScript =
    """
      |var x = 14;
      |if(x > 18) {
      |cancel(x, "Over Age!")
      |}
    """.stripMargin
  val jsHooks: JsHooks = JsHooks(onValidate = Some(validationScript), None, None, None, None, None, None)

  val migrationPlan: MigrationPlan = MigrationPlan(Map.empty)

  val context = RequestContext(tag = 1,
    auth = AuthInfo.Anonymous,
    pushChannel = None,
    meta = RequestMetadata(traceId = None,
      when = DateTime.now,
      host = "localhost",
      protocol = RequestProtocol.WebSockets)
  )

  val userModel = new Model(r, collMeta, fields, jsHooks, migrationPlan, List.empty)
  val input: JsObject = Json.obj("name" -> JsString("Ahmed"),
    "age" -> JsNumber(23),
    "address" -> Json.obj("streetName" -> JsString("BS"), "block" -> JsNumber(23)))

  "Validate JsHooks" should "pass if the JS validation script ended without calling cancel()" in {

    userModel.executeValidate(context, input) should be(Succeeded)

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
    userModel.executeValidate(context, input) should be(Terminated(401, "Over Age!"))
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
    userModel.executeValidate(context, input) should be(Succeeded)

  }

}
