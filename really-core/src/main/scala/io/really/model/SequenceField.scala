/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really.ReallyGlobals
import play.api.libs.json.{JsObject, JsResult, JsValue, JsPath}


case class SequenceField[T](
                             key: FieldKey,
                             dataType: DataType[T],
                             validationExpression: Option[JsScript],
                             default: Option[JsScript],
                             required: Boolean)(implicit val globals: ReallyGlobals) extends ActiveField[T] {
  override def read(root: JsPath, in: JsObject): JsResult[JsObject] = ???

  def runThroughValidator(path: JsPath, in: JsValue): JsResult[JsObject] = ???

  protected def readDefault(path: JsPath): JsResult[JsValue] = ???

}
