import scala.language.implicitConversions

package io {

import akka.actor.{Props, ActorSystem, ActorRef}
import io.really.model.ModelObject
import io.really.quickSand.QuickSand
import io.really.protocol._
import org.joda.time.DateTime
import play.api.libs.json.{JsError, JsObject}

package object really {

  type Revision = Long
  type CollectionName = String
  type Tokens = List[PathToken]
  type BucketID = String
  type Buckets = Map[R, ModelObject]

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

    def modelRegistryRouterProps: Props

    def collectionActorProps: Props

    def actorSystem: ActorSystem

    def receptionist: ActorRef

    def quickSand: QuickSand

    def modelRegistryRouter: ActorRef

    def collectionActor: ActorRef
  }

  trait RoutableByR {
    def r: R
  }

  trait RoutableToCollectionActor extends RoutableByR

  trait Request {
    val ctx: RequestContext
  }

  object Request {

    case class Subscribe(ctx: RequestContext, body: SubscriptionBody) extends Request

    case class Unsubscribe(ctx: RequestContext, body: UnsubscriptionBody) extends Request

    case class GetSubscription(ctx: RequestContext, r: R) extends Request with RoutableByR

    case class Get(ctx: RequestContext, r: R, cmdOpts: GetOpts) extends Request with RoutableByR

    case class Update(ctx: RequestContext, r: R, rev: Long, body: UpdateBody) extends Request with RoutableToCollectionActor

    case class Read(ctx: RequestContext, r: R, cmdOpts: ReadOpts) extends Request with RoutableByR

    case class Create(ctx: RequestContext, r: R, body: JsObject) extends Request with RoutableToCollectionActor

    case class Delete(ctx: RequestContext, r: R) extends Request with RoutableToCollectionActor

  }

  trait InternalRequest extends RoutableToCollectionActor

  trait InternalResponse extends Response

  trait Response

  trait Result extends Response

  object Result {

    case class SubscribeResult(subscriptions: Set[SubscriptionOpResult]) extends Result

    case class UnsubscribeResult(unsubscriptions: Set[SubscriptionOpResult]) extends Result

    case class GetSubscriptionResult(fields: Set[String]) extends Result

    //TODO change fields type
    case class GetResult(body: JsObject, fields: Set[String]) extends Result

    //TODO change fields type
    case class UpdateResult(snapshots: List[FieldSnapshot], rev: Long) extends Result

    case class ReadResult(body: ReadResponseBody, subscription: Option[String]) extends Result

    case class CreateResult(body: JsObject) extends Result

    case object DeleteResult extends Result

  }

  trait CommandError extends Response

  object CommandError {

    case class AlreadyExists(r: R) extends CommandError

    case class ModelValidationFailed(reason: JsError) extends CommandError

    case class ValidationFailed(reason: JsError) extends CommandError

    case class JSValidationFailed(reason: String) extends CommandError

    case class OutdatedRevision(request: Request) extends CommandError

    case class InvalidCommand(reason: String) extends CommandError

    case class InternalServerError(code: Int, reason: String) extends CommandError

    case object ObjectNotFound extends CommandError

    case class ParentNotFound(code: Int, reason: String) extends CommandError

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
