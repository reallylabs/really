/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really.js.JsResultHelpers
import play.api.libs.json._


trait Field[T] {
  def key: FieldKey

  def dataType: DataType[T]

  /** *
    * Reads the full json input object and cuts the fields it's interested in, validates, generates default value
    * if the field was missing in the input, returns only the portion of interest from this field `key -> value`
    * @param path
    * @param in the whole document
    * @return [[JsResult[JsObject]] instance that holds only the value of the field-in-interest
    */
  def read(path: JsPath, in: JsObject): JsResult[JsObject]
}


trait ActiveField[T] extends Field[T] {
  def required: Boolean

  def default: Option[JsScript]

  protected def runThroughValidator(path: JsPath, in: JsValue): JsResult[JsObject]

  protected def readDefault(path: JsPath): JsResult[JsValue]

  def read(root: JsPath, input: JsObject): JsResult[JsObject] = {
    //first extract only the value we are interested in
    val in = input \ key
    val path = root \ key
    // check if in == JsNull && required == true, if default isDefined, generate Default
    // if default is None, fail.
    // if Default is defined, generate default and continue the flow with this value
    (in, required) match {
      case (JsNull | _: JsUndefined, true) if default.isEmpty => //fail
        JsError(path, "value.required")
      case (JsNull | _: JsUndefined, false) if default.isEmpty => // default to set the value to null
        JsSuccess(Json.obj(key -> JsNull), path)
      case (JsNull | _: JsUndefined, _) if default.isDefined => // get default value
        JsResultHelpers.repath(path, readDefault(path).flatMap(a => validate(path, a)))
      case (e, _) => //run through validator
        JsResultHelpers.repath(path, validate(path, e))
    }
  }

  protected def validate(path: JsPath, in: JsValue): JsResult[JsObject] =
    if (in == JsNull)
      JsSuccess(Json.obj(key -> JsNull), path)
    else
      // validate that (in) is readable by dataType
      dataType.readJsValue(in).flatMap(runThroughValidator(path, _))

}


trait ReactiveField[T] extends Field[T]



