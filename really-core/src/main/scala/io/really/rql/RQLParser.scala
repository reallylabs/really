/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.rql

import io.really.rql.RQL.Query
import play.api.libs.json.{ JsValue, JsObject }

import scala.util.parsing.combinator._

object RQLParser {
  def parse(q: String, values: JsObject): Query = {
    val parser = new RQLParser(values)
    parser.parseAll(parser.query, q) match {
      case parser.Success(q, _) => q
      case parser.NoSuccess(message, in) =>
        throw new RQL.ParseError(message, in.pos)
    }
  }
}

class RQLParser(values: JsObject) extends JavaTokenParsers {

  val nameReg = """\p{javaJavaIdentifierStart}\p{javaJavaIdentifierPart}*""".r

  def CustomParserError[A](p: Parser[A], msg: String): Parser[A] = Parser[A] { i =>
    p(i) match {
      case Failure(_, in) => Failure(msg, in)
      case o => o
    }
  }

  def javaIdent: Parser[String] = CustomParserError(nameReg, "expected fieldName with valid format")

  def queryTerm: Parser[RQL.Term] = javaIdent ^^ { case s => RQL.Term(s) }

  def andOp: Parser[String] = CustomParserError("(?i)AND".r, "missing AND operator")

  def fieldKey: Parser[String] = CustomParserError(nameReg, "expected value key")

  def termValue: Parser[RQL.TermValue] = '$' ~> fieldKey ^^ {
    case fieldKey =>
      (values \ fieldKey).asOpt[JsValue] match {
        case Some(v) => RQL.TermValue(v)
        case None => throw new RQL.MissingValue(fieldKey)
      }
  }

  def operator: Parser[RQL.Operator] =
    CustomParserError(">=" | "<=" | ">" | "<" | "=" | "(?i)in".r, "expected supported operator") ^^ {
      case ">" => RQL.Operator.Gt
      case ">=" => RQL.Operator.Gte
      case "<" => RQL.Operator.Lt
      case "<=" => RQL.Operator.Lte
      case "=" => RQL.Operator.Eq
      case _ => RQL.Operator.IN
    }

  def simpleQuery: Parser[Query] = (positioned(queryTerm) ~ positioned(operator) ~ positioned(termValue)) ^^ {
    case t ~ o ~ i => RQL.SimpleQuery(t, o, i)
  }

  def compositeCombinator: Parser[Query] = (simpleQuery ~ (andOp ~ simpleQuery).*) ^^ {
    case c1 ~ l =>
      l.foldLeft(c1) {
        (a, b) => RQL.AndCombinator(a, b._2)
      }
  }

  def query: Parser[Query] = compositeCombinator

}