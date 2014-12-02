/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.rql

import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.util.parsing.input.{ Position, Positional }

object RQL {

  case class Term(term: String) extends Positional

  case class TermValue(value: JsValue) extends Positional

  trait Operator extends Positional

  object Operator {

    case object Gt extends Operator {
      override def toString() = ">"
    }

    case object Gte extends Operator {
      override def toString() = ">="
    }

    case object Lt extends Operator {
      override def toString() = "<"
    }

    case object Lte extends Operator {
      override def toString() = ">="
    }

    case object Eq extends Operator {
      override def toString() = "="
    }

    case object Between extends Operator {
      override def toString() = "BETWEEN"
    }

    case object IN extends Operator {
      override def toString() = "IN"
    }

  }

  abstract class Query {
    def isValid: Either[InvalidQuery, Unit]
  }

  case object EmptyQuery extends Query {
    def isValid: Either[InvalidQuery, Unit] = Right(())
  }

  case class SimpleQuery(key: Term, op: Operator, termValue: TermValue) extends Query with Positional {
    override def toString() =
      key.term + " " + op.toString + " " + termValue.value.toString

    def isValid: Either[InvalidQuery, Unit] = {
      (op, termValue.value) match {
        case (Operator.IN, JsArray(_)) => Right(())
        case (Operator.IN, _) => Left(InvalidQuery(s"The Query value must be JsArray in case of query operator is '$op'."))
        case (Operator.Gt, JsNumber(_)) => Right(())
        case (Operator.Gt, _) => Left(InvalidQuery(s"The Query value must be Number in case of query operator is '$op'."))
        case (Operator.Gte, JsNumber(_)) => Right(())
        case (Operator.Gte, _) => Left(InvalidQuery(s"The Query value must be Number in case of query operator is '$op'."))
        case (Operator.Lt, JsNumber(_)) => Right(())
        case (Operator.Lt, _) => Left(InvalidQuery(s"The Query value must be Number in case of query operator is '$op'."))
        case (Operator.Lte, JsNumber(_)) => Right(())
        case (Operator.Lte, _) => Left(InvalidQuery(s"The Query value must be Number in case of query operator is '$op'."))
        case (Operator.Between, arr @ JsArray(_)) if arr.value.size == 2 => Right(())
        case (Operator.Between, _) => Left(InvalidQuery(s"The Query value must be Array of two numbers in case of query operator is '$op'."))
        case _ => Right(())
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
            case Left(error) => JsError(Seq(JsPath() -> Seq(ValidationError(error.reason))))
          }
        case Left(e) => JsError(Seq(JsPath() -> Seq(ValidationError(e.message))))
      }
  }

  case class AndCombinator(q1: Query, q2: Query) extends Query with Positional {
    override def toString() =
      q1.toString + " AND " + q2.toString

    def isValid: Either[InvalidQuery, Unit] =
      q1.isValid match {
        case Right(_) => q2.isValid
        case left => left
      }
  }

  case class InvalidQuery(reason: String)

  trait RQLException

  case class ParseError(message: String)

  case class MissingValue(fieldKey: String)
    extends Exception(s"cannot find value for key `$fieldKey` in passed values") with RQLException
}

