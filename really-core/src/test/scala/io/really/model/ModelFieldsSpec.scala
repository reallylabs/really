/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really.R
import org.scalatest._
import play.api.data.validation.ValidationError
import play.api.libs.json._
import io.really.js._

class ModelFieldsSpec extends FlatSpec with Matchers {
  "ValueField" should "parse simple values correctly" in {
    val a = ValueField("name", DataType.RString, None, None, true)
    assert(a.read(JsPath(), Json.obj("name" -> "Ahmed Soliman")) == JsSuccess(Json.obj("name" -> "Ahmed Soliman"), JsPath() \ "name"))
    assert(a.read(JsPath(), Json.obj("name" -> 66)) == JsError((JsPath() \ "name", ValidationError("error.expected.jsstring"))))

    val b = ValueField("age", DataType.RLong, None, None, true)
    assert(b.read(JsPath(), Json.obj("age" -> 611)) == JsSuccess(Json.obj("age" -> 611), JsPath() \ "age"))
    assert(b.read(JsPath(), Json.obj("age" -> "Ahmed")) == JsError((JsPath() \ "age", ValidationError("error.expected.jsnumber"))))

  }

  it should "respect the javascript validator" in {
    val b = ValueField("age", DataType.RLong, Some("""age < 100"""), None, true)
    assert(b.read(JsPath(), Json.obj("age" -> 101)) == JsError((JsPath() \ "age", ValidationError("validation.custom.failed"))))
  }

  it should "execute the javascript validator really fast (200k cycles)" in {
    val b = ValueField("age", DataType.RLong, Some("""age < 100"""), None, true)
    for (i <- 1 to 200000) {
      b.read(JsPath(), Json.obj("age" -> 61))
    }
  }

  it should "work properly if grouped into List[Field[_]]" in {
    val fields: Map[String, Field[_]] = Map(
      "age" -> ValueField("age", DataType.RLong, Some("""age < 100"""), None, true),
      "name" -> ValueField("name", DataType.RString, None, None, true)
    )

    val obj = Json.obj("age" -> 99, "name" -> "Steve")
    val res = JsResultHelpers.merge(List(fields("age").read(JsPath(), obj), fields("name").read(JsPath(), obj)))
    assert(res == JsSuccess(Json.obj("age" -> 99, "name" -> "Steve")))
  }

  it should "generate a default value if default expression is set" in {
    val a = ValueField("age", DataType.RLong, Some("""age < 100"""), Some("""50"""), true)
    assert(a.read(JsPath(), Json.obj("v" -> "k")) == JsSuccess(Json.obj("age" -> 50), JsPath() \ "age"))
    val b = ValueField("age", DataType.RLong, Some("""age > 100"""), Some("""50"""), true)
    assert(b.read(JsPath(), Json.obj("v" -> "k")) == JsError((JsPath() \ "age", ValidationError("validation.custom.failed"))))

    val c = ValueField("age", DataType.RLong, Some("""age > 100"""), Some("""52348652346752347"""), true)
    assert(c.read(JsPath(), Json.obj("v" -> "k")) == JsSuccess(Json.obj("age" -> 52348652346752347l), JsPath() \ "age"))

    val d = ValueField("age", DataType.RLong, Some("""age <= 30"""), Some("""15 + 15"""), true)
    assert(d.read(JsPath(), Json.obj("v" -> "k")) == JsSuccess(Json.obj("age" -> 30), JsPath() \ "age"))
  }

  it should "return null when valueField not required and there is no default supported" in {
    val strA = ValueField("job", DataType.RString, Some("""job.length > 10 && job.split(' ').length > 2"""), None, false)
    assert(strA.read(JsPath(), Json.obj("v" -> "k")) == JsSuccess(Json.obj("job" -> JsNull), JsPath() \ "job"))
  }

  it should "return error when valueField is required and there is no default supported" in {
    val strA = ValueField("job", DataType.RString, Some("""job.length > 10 && job.split(' ').length > 2"""), None, true)
    assert(strA.read(JsPath(), Json.obj("v" -> "k")) == JsError((JsPath() \ "job", ValidationError("value.required"))))
  }

  it should "generate a default value if default expression is set for RString" in {
    val strA = ValueField("job", DataType.RString, Some("""job.length >= 10"""), Some(""""really developer""""), false)
    assert(strA.read(JsPath(), Json.obj("v" -> "k")) == JsSuccess(Json.obj("job" -> "really developer"), JsPath() \ "job"))

    val strB = ValueField("job", DataType.RString, Some("""job.length > 10 && job.split(' ').length >= 2"""), Some(""""really developer""""), false)
    assert(strB.read(JsPath(), Json.obj("v" -> "k")) == JsSuccess(Json.obj("job" -> "really developer"), JsPath() \ "job"))
  }

  it should "return error when valueField default doesn't match data type" in {
    val strC = ValueField("job", DataType.RString, Some("""job.length > 10 && job.split(' ').length > 2"""), Some("""3.15"""), false)
    assert(strC.read(JsPath(), Json.obj("v" -> "k")) == JsError((JsPath() \ "job", ValidationError("field.default.invalid_return_type"))))
  }

  it should "return error when valueField value doesn't pass validation script " in {
    val strC = ValueField("job", DataType.RString, Some("""job.length > 10 && job.split(' ').length > 2"""), Some(""""really developer""""), false)
    assert(strC.read(JsPath(), Json.obj("v" -> "k")) == JsError((JsPath() \ "job", ValidationError("validation.custom.failed"))))
  }

  it should "return error when valueField value with validation script deosn't return boolean " in {
    val strC = ValueField("job", DataType.RString, Some("""job.length"""), Some(""""really developer""""), false)
    assert(strC.read(JsPath(), Json.obj("v" -> "k")) == JsError((JsPath() \ "job", ValidationError("validation.custom.invalid_return_type"))))
  }

  "CalculatedField" should "return the proper value based on simple js function" in {
    val dep1 = ValueField("age", DataType.RLong, Some("""age < 100"""), Some("""50"""), true)
    val a = CalculatedField1("spatialAge", DataType.RLong,
      """
        |function calculate(age) {
        |  return age + 10;
        |}
      """.stripMargin, dep1)

    assert(a.read(JsPath(), Json.obj("age" -> 441)) == JsSuccess(Json.obj("spatialAge" -> 451), JsPath() \ "spatialAge"))
  }

  it should "handle missing dependency properly" in {
    val dep1 = ValueField("age", DataType.RLong, Some("""age < 100"""), Some("""50"""), true)
    val a = CalculatedField1("spatialAge", DataType.RLong,
      """
        |function calculate(age) {
        | print(age)
        | if (age != undefined)
        |   return age + 10;
        | else
        |   return 221;
        |}
      """.stripMargin, dep1)
    assert(a.read(JsPath(), Json.obj("name" -> "John")) == JsSuccess(Json.obj("spatialAge" -> 221), JsPath() \ "spatialAge"))
  }

  it should "handle three dependencies properly" in {
    val dep1 = ValueField("age", DataType.RLong, Some("""age < 100"""), Some("""50"""), true)
    val dep2 = ValueField("extra", DataType.RLong, None, None, true)
    val dep3 = ValueField("count", DataType.RString, None, None, true)
    val a = CalculatedField3("spatialAge", DataType.RLong,
      """
        |function calculate(age, extra, count) {
        |  print(parseInt(count));
        |  return extra + age + parseInt(count);
        |}
      """.stripMargin, dep1, dep2, dep3)
    assert(a.read(JsPath(), Json.obj("age" -> 5, "extra" -> 15, "count" -> "20")) == JsSuccess(Json.obj("spatialAge" -> 40), JsPath() \ "spatialAge"))

  }

  it should "handle two dependencies properly" in {
    val dep1 = ValueField("age", DataType.RLong, Some("""age < 100"""), Some("""50"""), true)
    val dep2 = ValueField("extra", DataType.RLong, None, None, true)
    val a = CalculatedField2("spatialAge", DataType.RLong,
      """
        |function calculate(age, extra) {
        |  return extra + age;
        |}
      """.stripMargin, dep1, dep2)
    assert(a.read(JsPath(), Json.obj("age" -> 5, "extra" -> 15)) == JsSuccess(Json.obj("spatialAge" -> 20), JsPath() \ "spatialAge"))
  }

  it should "return error if return type miss match with calculated field type" in {
    val dep1 = ValueField("age", DataType.RLong, Some("""age < 100"""), Some("""50"""), true)
    val dep2 = ValueField("extra", DataType.RLong, None, None, true)
    val a = CalculatedField2("spatialAge", DataType.RLong,
      """
        |function calculate(age, extra) {
        |  return (extra + age ).toString();
        |}
      """.stripMargin, dep1, dep2)
    assert(a.read(JsPath(), Json.obj("age" -> 5, "extra" -> 15)) == JsError(JsPath() \ "spatialAge", ValidationError("field.default.invalid_return_type")))
  }

  //  it should "return error if one of dependant type miss match with its field type" in {
  //    val dep1 = ValueField("age", DataType.RLong, Some( """age < 100"""), Some( """50"""), true)
  //    val dep2 = ValueField("extra", DataType.RLong, None, Some( """50.5443"""), true)
  //    val a = CalculatedField2("spatialAge", DataType.RLong,
  //      """
  //        |function calculate(age, extra) {
  //        |  return extra + age ;
  //        |}
  //      """.stripMargin, dep1, dep2)
  //    assert(a.read(JsPath(), Json.obj("age" -> 5, "extra" -> JsNull )) == JsError(JsPath() \ "extra" , ValidationError("field.default.invalid_return_type")))
  //  }

  "ReferenceField" should "parse correctly" in {
    val a = ReferenceField("creator", true, R("/users"), List("firstName", "lastName"))
    assert(a.read(JsPath(), Json.obj("creator" -> "/users/12348905/")) == JsSuccess(Json.obj("creator" -> "/users/12348905/"), JsPath() \ "creator"))
    assert(a.read(JsPath(), Json.obj("creator" -> "/products/2345678")) == JsError((JsPath() \ "creator", ValidationError("error.invalid.R"))))
    assert(a.read(JsPath(), Json.obj("creator" -> "amal")) == JsError((JsPath() \ "creator", ValidationError("error.invalid.R"))))

  }
}