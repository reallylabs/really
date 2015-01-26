/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.rql

import io.really.model.Field
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.util.parsing.input.Positional

object RQL {

  case class Term(term: String) extends Positional

  case class TermValue(value: JsValue) extends Positional

  trait Operator extends Positional {
    def applyOn(right: JsValue, left: JsValue): Boolean
  }

  object Operator {

    case object Gt extends Operator {
      override def toString() = ">"

      def applyOn(right: JsValue, left: JsValue): Boolean =
        (right, left) match {
          case (JsNumber(r), JsNumber(l)) => r > l
          case (JsString(r), JsString(l)) => r > l
          case _ => false
        }
    }

    case object Gte extends Operator {
      override def toString() = ">="

      def applyOn(right: JsValue, left: JsValue): Boolean =
        (right, left) match {
          case (JsNumber(r), JsNumber(l)) => r >= l
          case (JsString(r), JsString(l)) => r >= l
          case _ => false
        }
    }

    case object Lt extends Operator {
      override def toString() = "<"

      def applyOn(right: JsValue, left: JsValue): Boolean =
        (right, left) match {
          case (JsNumber(r), JsNumber(l)) => r < l
          case (JsString(r), JsString(l)) => r < l
          case _ => false
        }
    }

    case object Lte extends Operator {
      override def toString() = ">="

      def applyOn(right: JsValue, left: JsValue): Boolean =
        (right, left) match {
          case (JsNumber(r), JsNumber(l)) => r >= l
          case (JsString(r), JsString(l)) => r >= l
          case _ => false
        }
    }

    case object Eq extends Operator {
      override def toString() = "="

      def applyOn(right: JsValue, left: JsValue): Boolean =
        right == left
    }

    case object Between extends Operator {
      override def toString() = "BETWEEN"

      def applyOn(right: JsValue, left: JsValue): Boolean =
        (right, left) match {
          case (JsNumber(r), JsArray(Seq(JsNumber(num1), JsNumber(num2)))) => r > num1 && r < num2
          case (JsString(r), JsArray(Seq(JsString(num1), JsString(num2)))) => r > num1 && r < num2
          case _ => false
        }
    }

    case object IN extends Operator {
      override def toString() = "IN"

      def applyOn(right: JsValue, left: JsValue): Boolean =
        left match {
          case JsArray(values) => values.contains(right)
          case _ => false
        }
    }

  }

  abstract class Query {
    def isValid: Either[ValidationError, RQLSuccess]

    /**
     * Validate that this object match this query or not
     * @param obj
     * @param fields
     * @return true or false
     */
    def validateObject(obj: JsObject, fields: Set[Field[_]]): Boolean
  }

  case object EmptyQuery extends Query {
    def isValid: Either[ValidationError, RQLSuccess] = Right(RQLSuccess(this))

    def validateObject(obj: JsObject, fields: Set[Field[_]]): Boolean = true

  }

  case class SimpleQuery(key: Term, op: Operator, termValue: TermValue) extends Query with Positional {
    override def toString() =
      key.term + " " + op.toString + " " + termValue.value.toString

    def isValid: Either[ValidationError, RQLSuccess] = {
      (op, termValue.value) match {
        case (Operator.IN, JsArray(_)) => Right(RQLSuccess(this))
        case (Operator.IN, _) =>
          Left(ValidationError(s"The Query value of `${key.term}` must be JsArray in case of query operator is '$op'."))
        case (Operator.Between, arr @ JsArray(_)) if arr.value.size == 2 =>
          Right(RQLSuccess(this))
        case (Operator.Between, _) =>
          Left(ValidationError(s"The Query value must be Array of two numbers in case of query operator is '$op'."))
        case _ => Right(RQLSuccess(this))
      }
    }

    def validateObject(obj: JsObject, fields: Set[Field[_]]): Boolean = {
      fields.find(_.key == key.term) match {
        case Some(f) => obj.validate((__ \ f.key).read[JsValue]) match {
          case JsSuccess(v, _) => op.applyOn(v, termValue.value)
          case JsError(e) => false
        }
        case None => false
      }
    }

  }

  object Query {
    /*
   * JSON Reads for Query
   */
    implicit object QueryReads extends Reads[Query] {
      val filterReads = (__ \ 'filter).read[String]
      val valuesReads = (__ \ 'values).read[JsObject]

      def reads(jsObj: JsValue): JsResult[Query] = jsObj match {
        case queryObject: JsObject =>
          jsObj.validate((filterReads and valuesReads).tupled) match {
            case JsSuccess((filter, jsvalues), _) => parseAndValidate(filter, jsvalues)
            case e: JsError => e
          }
        case _ =>
          JsError(Seq(JsPath() -> Seq(ValidationError("error.unsupported.query"))))
      }
    }

    private def parseAndValidate(filter: String, values: JsObject): JsResult[Query] =
      RQLParser.parse(filter, values) match {
        case Right(q) =>
          q.isValid match {
            case Right(_) => JsSuccess(q)
            case Left(error) => JsError(Seq(JsPath() -> Seq(error)))
          }
        case Left(e) => JsError(Seq(JsPath() -> Seq(ValidationError(e.message))))
      }
  }

  case class AndCombinator(q1: Query, q2: Query) extends Query with Positional {
    override def toString() =
      q1.toString + " AND " + q2.toString

    def isValid: Either[ValidationError, RQLSuccess] =
      q1.isValid match {
        case Right(_) => q2.isValid
        case left => left
      }

    def validateObject(obj: JsObject, fields: Set[Field[_]]): Boolean =
      q1.validateObject(obj, fields) && q2.validateObject(obj, fields)

  }

  trait RQLException

  case class ParseError(message: String)

  case class RQLSuccess(query: Query)

  case class MissingValue(fieldKey: String)
    extends Exception(s"cannot find value for key `$fieldKey` in passed values") with RQLException
}

