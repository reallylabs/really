/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import javax.script.{ ScriptException, Invocable }

import _root_.io.really.js.JsTools
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.{ Success, Failure }

abstract class CalculatedField[T] extends ReactiveField[T] {
  def calculatorExpression: JsScript

  def dependsOn: List[FieldKey]

  @transient
  lazy val engine = JsTools.newEngineWithSDK()
  @transient
  lazy val bindings = JsTools.getBindings(engine)
  @transient
  lazy val undefined = bindings.get("undefined")
  @transient
  private[this] lazy val calculator: Invocable = {
    val template = s"""var calculate = ${calculatorExpression.trim};"""
    engine.eval(template)
    engine.asInstanceOf[Invocable]
  }

  def inputs(in: JsObject): List[Object]

  def read(root: JsPath, in: JsObject): JsResult[JsObject] = {
    val path = root \ key
    try {
      dataType.writeJsValue(calculator.invokeFunction("calculate", inputs(in): _*)) match {
        case Success(v) => JsSuccess(Json.obj(key -> v), path)
        case Failure(e) => JsError((path, ValidationError("field.default.invalid_return_type")))
      }
    } catch {
      case e: ScriptException =>
        val l = e.getLineNumber
        val c = e.getColumnNumber
        JsError((path, ValidationError("field.default.script_exception", s"at $l:$c", e.getMessage)))
    }
  }

}

case class CalculatedField1[T, A](
    key: FieldKey,
    dataType: DataType[T],
    calculatorExpression: JsScript,
    dep1: ActiveField[A]
) extends CalculatedField[T] {
  val dependsOn = List(dep1.key)

  def inputs(in: JsObject): List[Object] = {
    val dep1Value = in \ dep1.key
    List(dep1.dataType.valueAsOpt(dep1Value).map(_.asInstanceOf[Object]).getOrElse(undefined))
  }
}

case class CalculatedField2[T, A, B](
    key: FieldKey,
    dataType: DataType[T],
    calculatorExpression: JsScript,
    dep1: ActiveField[A], dep2: ActiveField[B]
) extends CalculatedField[T] {
  val dependsOn = List(dep1.key, dep2.key)

  def inputs(in: JsObject): List[Object] = {
    val dep1Value = in \ dep1.key
    val dep2Value = in \ dep2.key
    List(
      dep1.dataType.valueAsOpt(dep1Value).map(_.asInstanceOf[Object]).getOrElse(undefined),
      dep2.dataType.valueAsOpt(dep2Value).map(_.asInstanceOf[Object]).getOrElse(undefined)
    )
  }
}

case class CalculatedField3[T, A, B, C](
    key: FieldKey,
    dataType: DataType[T],
    calculatorExpression: JsScript,
    dep1: ActiveField[A], dep2: ActiveField[B], dep3: ActiveField[C]
) extends CalculatedField[T] {
  val dependsOn = List(dep1.key, dep2.key, dep3.key)

  def inputs(in: JsObject): List[Object] = {
    val dep1Value = in \ dep1.key
    val dep2Value = in \ dep2.key
    val dep3Value = in \ dep3.key
    List(
      dep1.dataType.valueAsOpt(dep1Value).map(_.asInstanceOf[Object]).getOrElse(undefined),
      dep2.dataType.valueAsOpt(dep2Value).map(_.asInstanceOf[Object]).getOrElse(undefined),
      dep3.dataType.valueAsOpt(dep3Value).map(_.asInstanceOf[Object]).getOrElse(undefined)
    )
  }
}

