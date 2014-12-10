/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

import scala.language.implicitConversions

package io {

  import play.api.data.validation.ValidationError
  import reactivemongo.api.DefaultDB
  import akka.actor.{ Props, ActorSystem, ActorRef }
  import io.really.model.DataObject
  import io.really.quickSand.QuickSand
  import io.really.protocol._
  import org.joda.time.DateTime
  import play.api.libs.json._

  package object really {
    type CID = String
    type Revision = Long
    type CollectionName = String
    type Tokens = List[PathToken]
    type BucketID = String
    type Buckets = Map[R, DataObject]
    type AppId = String

    implicit def IntToToken(id: Int): TokenId = LongToToken(id)

    implicit def LongToToken(id: Long): TokenId = R.IdValue(id)

    implicit def TupleTokenToToken(token: (String, TokenId)): PathToken = PathToken(token._1, token._2)

    implicit def TupleLongToToken(token: (String, Long)): PathToken = PathToken(token._1, token._2)

    implicit def TupleIntToToken(token: (String, Int)): PathToken = PathToken(token._1, token._2)

    trait ReallyGlobals {
      def config: ReallyConfig

      def boot(): Unit

      def shutdown(): Unit

      def requestProps(ctx: RequestContext, replyTo: ActorRef, cmd: String, body: JsObject): Props

      def receptionistProps: Props

      def modelRegistryProps: Props

      def requestRouterProps: Props

      def persistentModelStoreProps: Props

      def collectionActorProps: Props

      def gorillaEventCenterProps: Props

      def subscriptionManagerProps: Props

      def readHandlerProps: Props

      def materializerProps: Props

      def actorSystem: ActorSystem

      def receptionist: ActorRef

      def quickSand: QuickSand

      def modelRegistry: ActorRef

      def requestRouter: ActorRef

      def persistentModelStore: ActorRef

      def collectionActor: ActorRef

      def gorillaEventCenter: ActorRef

      def materializerView: ActorRef

      def mongodbConntection: DefaultDB

      def subscriptionManager: ActorRef

      def readHandler: ActorRef

      def mediator: ActorRef

    }

    trait withRequestContext {
      val ctx: RequestContext
    }

    trait RoutableByR {
      def r: R
    }

    trait RoutableToCollectionActor extends RoutableByR

    trait RoutableToReadHandler extends RoutableByR

    trait RoutableToSubscriptionManager

    trait Request extends withRequestContext

    object Request {

      case class Subscribe(ctx: RequestContext, body: SubscriptionBody) extends Request with RoutableToSubscriptionManager

      case class Unsubscribe(ctx: RequestContext, body: UnsubscriptionBody) extends Request with RoutableToSubscriptionManager

      case class GetSubscription(ctx: RequestContext, r: R) extends Request with RoutableByR with RoutableToSubscriptionManager

      case class Get(ctx: RequestContext, r: R, cmdOpts: GetOpts) extends Request with RoutableToReadHandler {
        require(r.isObject, "r should be represent object")
      }

      case class Update(ctx: RequestContext, r: R, rev: Long, body: UpdateBody) extends Request with RoutableToCollectionActor {
        require(r.isObject, "r should be represent object")
      }

      case class Read(ctx: RequestContext, r: R, cmdOpts: ReadOpts) extends Request with RoutableToReadHandler {
        require(r.isCollection, "r should be represent collection")
      }

      case class Create(ctx: RequestContext, r: R, body: JsObject) extends Request with RoutableToCollectionActor

      case class Delete(ctx: RequestContext, r: R) extends Request with RoutableToCollectionActor {
        require(r.isObject, "r should be represent object")
      }

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
      case class UpdateResult(rev: Revision) extends Result

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

    case class Application(name: String)

    /**
     * Represent Reads and Writes for Application
     */
    object Application {
      implicit val fmt = Json.format[Application]
    }

    trait AuthProvider

    object AuthProvider {

      case object Email extends AuthProvider

      case object Anonymous extends AuthProvider

    }

    /*
   * Represent implicit format for AuthProvider
   */
    implicit object AuthProviderFmt extends Format[AuthProvider] {

      import AuthProvider._

      def reads(json: JsValue) = json match {
        case JsString("email") => JsSuccess(Email)
        case JsString("anonymous") => JsSuccess(Anonymous)
        case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.unsupported.provider"))))
      }

      def writes(o: AuthProvider): JsString = o match {
        case Email => JsString("email")
        case Anonymous => JsString("anonymous")
      }
    }

    case class UserInfo(provider: AuthProvider, userR: R, app: Application)

    /**
     * Represent Reads and Writes for UserInfo
     */
    object UserInfo {
      implicit val fmt = Json.format[UserInfo]
    }

    trait RequestProtocol

    object RequestProtocol {

      case object WebSockets extends RequestProtocol

      case object REST extends RequestProtocol

    }

    case class RequestMetadata(traceId: Option[String], when: DateTime, host: String, protocol: RequestProtocol)

    case class RequestContext(tag: Long, auth: UserInfo, pushChannel: Option[ActorRef], meta: RequestMetadata)

  }

}
