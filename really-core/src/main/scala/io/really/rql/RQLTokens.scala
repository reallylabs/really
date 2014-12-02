/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.rql

import play.api.data.validation.ValidationError
import play.api.libs.json._

object RQLTokens {

  case class PaginationToken(id: Long, direction: Int) {
    require(direction == 0 | direction == 1, s"paginationToken '$id:$direction' direction must be 0 or 1.")
  }

  object PaginationToken {
    def parse(token: String): JsResult[PaginationToken] = {
      token.split(":").toList match {
        case id :: dir :: Nil =>
          try {
            JsSuccess(PaginationToken(id.toLong, dir.toInt))
          } catch {
            case e: NumberFormatException =>
              JsError(Seq(JsPath() -> Seq(ValidationError("error.invalid.token.format"))))
          }
        case _ =>
          JsError(Seq(JsPath() -> Seq(ValidationError("error.invalid.token.format"))))
      }
    }

    implicit val writes = new Writes[PaginationToken] {
      def writes(token: PaginationToken): JsValue = JsString(s"${token.id}:${token.direction}")
    }

    implicit val reads = new Reads[PaginationToken] {
      def reads(jsToken: JsValue): JsResult[PaginationToken] =
        jsToken match {
          case JsString(token) => parse(token)
          case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.String"))))
        }
    }
  }

}