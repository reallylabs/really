/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package really.io.auth.controllers

import io.really.quickSand.QuickSand
import io.really.token.generator.{ AuthType, JWTGenerator }
import org.joda.time.DateTime
import play.api._
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._

object AuthController extends Controller {

  lazy val reallyConfig = Play.current.configuration.getConfig("really").getOrElse(Configuration.empty)
  lazy val secret = reallyConfig.getString("io.security.secret-key")

  val tokenGenerator = JWTGenerator(secret.get)

  val anonymousTokenDuration = reallyConfig.getMilliseconds("token.anonymous.duration")

  //GET Quicksand instance
  val quicksandConfig = reallyConfig.getConfig("quicksand").getOrElse(Configuration.empty)
  val workerId: Long = quicksandConfig.getLong("workerId").getOrElse(1)
  val datacenterId: Long = quicksandConfig.getLong("datacenterId").getOrElse(1)
  val reallyEpoch: Long = quicksandConfig.getLong("reallyEpoch").getOrElse(1410765153)
  val idGenerator = new QuickSand(workerId, datacenterId, reallyEpoch)

  def doAnonymousLogin = Action { implicit req =>
    val body = req.body.asJson.getOrElse(Json.obj()).as[JsObject]
    val expiresAt = anonymousTokenDuration map (DateTime.now().plus(_))

    val token = tokenGenerator.createAnonymousToken(idGenerator.nextId().toString, expiresAt, body)
    Ok(Json.obj("authType" -> AuthType.Anonymous.toString.toLowerCase, "accessToken" -> token))
  }

}