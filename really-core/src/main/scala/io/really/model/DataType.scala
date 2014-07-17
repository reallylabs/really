package io.really.model

import io.really.js.JsResultHelpers
import play.api.libs.json._
import scala.util.{Failure, Success, Try}

class DataTypeException(msg: String) extends Exception(msg)

trait DataType[T] {

  protected def fmt: Format[T]

  def readJsValue(in: JsValue): JsResult[JsValue] = in.validate(fmt).map(a => fmt.writes(a))


  def readValue(in: JsValue): JsResult[T] = in.validate(fmt)

  def valueAsOpt(in: JsValue): Option[T] = readValue(in).asOpt

  protected def getNativeValue(in: Object): T = in.asInstanceOf[T]

  def writeJsValue(in: Object): Try[JsValue] =
    try {
      Success(Json.toJson(getNativeValue(in))(fmt))
    } catch {
      case a: ClassCastException =>
        println("[TODO FIX ME] Could not cast the js snippet return:" + a.getMessage);
        Failure(new DataTypeException("input data type does not match the field type:" + a.getMessage))
    }
}

object DataType {

  case object RString extends DataType[String] {
    protected def fmt = implicitly[Format[String]]
  }

  case object RLong extends DataType[Long] {
    protected def fmt = implicitly[Format[Long]]

    protected override def getNativeValue(in: Object): Long = in match {
      case a: java.lang.Integer => a.longValue()
      case b: java.lang.Long => b
      case c: java.lang.Double => c.longValue()
      case _ => super.getNativeValue(in)
    }
  }

  case object RDouble extends DataType[Double] {
    protected def fmt = implicitly[Format[Double]]
  }

  //todo: this is how to define a custom type
  //case class CustomType(a: ) extends DataType
}