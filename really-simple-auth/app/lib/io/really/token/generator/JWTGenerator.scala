/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.token.generator

import org.joda.time._

import io.really.jwt.JWT
import io.really.token.generator.AuthType._
import play.api.libs.json._

/**
 * JWT generator for really tokens
 * @param secret represent really secret key, this secret will be generated
 * when creating an application with really
 */
case class JWTGenerator(secret: String) {
  /**
   * create anonymous really token
   * @param uid a unique identifier that identify a user
   * @param expires Expiration Date/Time
   * @param data The token can contains other info about the user which depends on the authentication method
   * @return token encoded as string
   */
  def createAnonymousToken(uid: String, expires: Option[DateTime] = None, data: JsObject = Json.obj()): String = {
    val exp = expires.map(_.getMillis).getOrElse(0l)
    val payload = Json.obj(
      "uid" -> uid,
      "authType" -> Anonymous.toString.toLowerCase,
      "expires" -> exp,
      "data" -> data
    )
    JWT.encode(secret, payload)
  }

  /**
   * create auth really token
   * @param r represent a unique identifier (R) for a persisted user
   * @param authType the authentication method used to generate this token,
   * currently supported methods is ("password", "anonymous")
   * @param uid a unique identifier that identify a user
   * @param expires Expiration Date/Time
   * @param data the token can contains other info about the user which depends on the authentication method
   * @return token encoded as string
   */
  def createAuthToken(r: String, authType: AuthType, uid: String, expires: Option[DateTime] = None,
    data: JsObject = Json.obj()): String = {
    val exp = expires.map(_.getMillis).getOrElse(0l)
    val payload = Json.obj(
      "_r" -> r,
      "uid" -> uid,
      "authType" -> authType.toString.toLowerCase,
      "expires" -> exp,
      "data" -> data
    )
    JWT.encode(secret, payload)
  }

}