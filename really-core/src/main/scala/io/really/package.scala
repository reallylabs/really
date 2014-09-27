import scala.language.implicitConversions

package io {

import akka.actor.{Props, ActorSystem, ActorRef}
import io.really.quickSand.QuickSand
import io.really.protocol._
import org.joda.time.DateTime
import play.api.libs.json.JsObject

package object really {

  type Revision = Long
  type CollectionName = String
  type Tokens = List[PathToken]

  implicit def IntToToken(id: Int): TokenId = LongToToken(id)

  implicit def LongToToken(id: Long): TokenId = R.IdValue(id)

  implicit def TupleTokenToToken(token: (String, TokenId)): PathToken = PathToken(token._1, token._2)

  implicit def TupleLongToToken(token: (String, Long)): PathToken = PathToken(token._1, token._2)

  implicit def TupleIntToToken(token: (String, Int)): PathToken = PathToken(token._1, token._2)

  trait ReallyGlobals {
    def config: ReallyConfig

    def boot(): Unit

    def requestProps(context: RequestContext, replyTo: ActorRef, body: JsObject): Props

    def receptionistProps: Props

    def actorSystem: ActorSystem

    def receptionist: ActorRef

    def quickSand: QuickSand
  }

  trait Request {
    val ctx: RequestContext
  }

  object Request {

    case class Subscribe(ctx: RequestContext, body: SubscriptionBody) extends Request

    case class Unsubscribe(ctx: RequestContext, body: UnsubscriptionBody) extends Request

    case class GetSubscription(ctx: RequestContext, r: R) extends Request

    case class Get(ctx: RequestContext, r: R, cmdOpts: GetOpts) extends Request

    case class Update(ctx: RequestContext, r: R, rev: Int, cmdOpts: UpdateOpts, body: UpdateBody) extends Request

    case class Read(ctx: RequestContext, r: R, cmdOpts: ReadOpts) extends Request

    case class Create(ctx: RequestContext, r: R, body: JsObject) extends Request

    case class Delete(ctx: RequestContext, r: R) extends Request

  }

  trait Response

  object Response {

    case class SubscribeResult(subscriptions: Set[SubscriptionOpResult]) extends Response

    case class UnsubscribeResult(unsubscriptions: Set[SubscriptionOpResult]) extends Response

    case class GetSubscriptionResult(fields: Set[String]) extends Response

    //TODO change fields type
    case class GetResult(body: JsObject, fields: Set[String]) extends Response

    //TODO change fields type
    case class UpdateResult(snapshots: List[FieldSnapshot], rev: Int) extends Response

    case class ReadResult(body: ReadResponseBody, subscription: Option[String]) extends Response

    case class CreateResult(body: JsObject) extends Response

    case object DeleteResult extends Response

  }

  trait Application

  trait AuthInfo

  object AuthInfo {

    case object Anonymous extends AuthInfo

    case class UserInfo(userId: String, userR: R, app: Application)

  }

  trait RequestProtocol

  object RequestProtocol {

    case object WebSockets extends RequestProtocol

    case object REST extends RequestProtocol

  }

  case class RequestMetadata(traceId: Option[String], when: DateTime, host: String, protocol: RequestProtocol)

  case class RequestContext(tag: Int, auth: AuthInfo, pushChannel: Option[ActorRef], meta: RequestMetadata)

}

}
