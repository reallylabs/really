/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.js

import play.api.libs.json._

object JsResultHelpers {
  def merge(results: List[JsResult[JsObject]]): JsResult[JsObject] = results.foldLeft(JsSuccess(Json.obj()): JsResult[JsObject]) {
    (r: JsResult[JsObject], acc: JsResult[JsObject]) =>
      (r, acc) match {
        case (JsSuccess(o1, _), JsSuccess(o2, _)) => JsSuccess(o1 ++ o2)
        case (a: JsSuccess[_], b: JsError) => b
        case (a: JsError, b:JsSuccess[_]) => a
        case (a: JsError, b: JsError) => b ++ a
      }
  }

  def repath[T](path: JsPath, in: JsResult[T]): JsResult[T] = in match {
    case a: JsSuccess[T] => a.copy(path = path)
    case b: JsError => b.copy(b.errors.map((k) => (path, k._2)))
  }
}