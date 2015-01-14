/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

import scala.language.implicitConversions

package io {

  import akka.event.LoggingAdapter
  import play.api.data.validation.ValidationError
  import reactivemongo.api.DefaultDB
  import akka.actor.{ Props, ActorSystem, ActorRef }
  import io.really.model.{ FieldKey, DataObject }
  import io.really.quickSand.QuickSand
  import io.really.protocol._
  import io.really.gorilla.RSubscription
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
    type EventType = String
    type Tag = Long
    type UID = String

    implicit def IntToToken(id: Int): TokenId = LongToToken(id)

    implicit def LongToToken(id: Long): TokenId = R.IdValue(id)

    implicit def TupleTokenToToken(token: (String, TokenId)): PathToken = PathToken(token._1, token._2)

    implicit def TupleLongToToken(token: (String, Long)): PathToken = PathToken(token._1, token._2)

    implicit def TupleIntToToken(token: (String, Int)): PathToken = PathToken(token._1, token._2)

    class ReallyConfigException(reason: String) extends Exception(reason)

    trait ReallyGlobals {
      def config: ReallyConfig

      def boot(): Unit

      def shutdown(): Unit

      def requestProps(ctx: RequestContext, replyTo: ActorRef, cmd: String, body: JsObject): Props

      def modelRegistryProps: Props

      def requestRouterProps: Props

      def objectSubscriberProps(rSubscription: RSubscription): Props

      def replayerProps(rSubscription: RSubscription, objectSubscriber: ActorRef, maxMarker: Option[Revision]): Props

      def receptionistProps: Props

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

      def mongodbConnection: DefaultDB

      def subscriptionManager: ActorRef

      def readHandler: ActorRef

      def mediator: ActorRef

      def logger: LoggingAdapter

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

    sealed trait Request extends withRequestContext

    object Request {

      case class SubscribeOnObject(ctx: RequestContext, body: SubscriptionBody, pushChannel: ActorRef) extends Request with RoutableToSubscriptionManager

      case class UnsubscribeFromObject(ctx: RequestContext, body: UnsubscriptionBody, pushChannel: ActorRef) extends Request with RoutableToSubscriptionManager

      case class GetSubscription(ctx: RequestContext, r: R) extends Request with RoutableByR with RoutableToSubscriptionManager

      case class Get(ctx: RequestContext, r: R, cmdOpts: GetOpts) extends Request with RoutableToReadHandler {
        require(r.isObject, "r should be represent object")
      }

      case class Update(ctx: RequestContext, r: R, rev: Long, body: UpdateBody) extends Request with RoutableToCollectionActor {
        require(r.isObject, "r should be represent object")
      }

      case class Read(ctx: RequestContext, r: R, cmdOpts: ReadOpts, pushChannel: ActorRef) extends Request with RoutableToReadHandler {
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

    sealed trait Result extends Response

    object Result {

      case class SubscribeResult(subscriptions: Set[SubscriptionOpResult]) extends Result

      case class UnsubscribeResult(unsubscriptions: Set[R]) extends Result

      case class GetResult(r: R, body: JsObject, fields: Set[FieldKey]) extends Result

      case class UpdateResult(r: R, rev: Revision) extends Result

      case class ReadResult(r: R, body: ReadResponseBody, subscription: Option[String]) extends Result

      case class CreateResult(r: R, body: JsObject) extends Result

      case class DeleteResult(r: R) extends Result
    }

    sealed trait CommandError extends Response {
      def r: Option[R]

      def error: ProtocolError.Error
    }

    object CommandError {

      import ProtocolError.Error

      def unapply(error: CommandError): Option[(Option[R], ProtocolError.Error)] =
        Some(error.r -> error.error)

      case class AlreadyExists(_r: R) extends CommandError {
        val r = Some(_r)
        val error = Error(503, "object.already.exist", None)
      }

      case class ModelValidationFailed(_r: R, reason: JsError) extends CommandError {
        val r = Some(_r)
        val error = Error(409, "validation.model.failed", Some(reason))
      }

      case class ValidationFailed(reason: JsError, r: Option[R] = None) extends CommandError {
        val error = Error(409, "validation.failed", Some(reason))
      }

      case class JSValidationFailed(_r: R, reason: String) extends CommandError {
        val r = Some(_r)
        val error = Error(409, reason, None)
      }

      case class OutdatedRevision(_r: R, request: Request) extends CommandError {
        val r = Some(_r)
        val error = Error(420, "object.revision.outdated", None)
      }

      case class InvalidCommand(cmd: String, r: Option[R] = None) extends CommandError {
        val reason = s"Invalid command: $cmd"
        val error = Error(454, reason, None)
      }

      case class InternalServerError(reason: String, r: Option[R] = None) extends CommandError {
        val error = Error(500, reason, None)
      }

      object InternalServerError {
        lazy val default = InternalServerError("internal.server.error")
      }

      case class ObjectNotFound(_r: R) extends CommandError {
        val r = Some(_r)
        val error = Error(404, "object.missing", None)
      }

      case class ObjectGone(_r: R) extends CommandError {
        val r = Some(_r)
        val error = Error(410, "object.gone", None)
      }

      case object ModelNotFound extends CommandError {
        val r = None
        val error = Error(420, "model.missing", None)
      }

      case class ParentNotFound(_r: R) extends CommandError {
        val r = Some(_r)
        val error = Error(404, "object.parent.missing", None)
      }

      case object BadJson extends CommandError {
        val r = None
        val error = Error(400, "json.malformed", None)
      }

      case object SocketIsIdle extends CommandError {
        val r = None
        val error = Error(408, "socket.idle", None)
      }

      case object InvalidCommandWhileUninitialized extends CommandError {
        val r = None
        val error = Error(400, "initialize.required", None)
      }

      case object InvalidInitialize extends CommandError {
        val r = None
        val error = Error(400, "initialize.invalid", None)
      }

      case object InvalidAccessToken extends CommandError {
        val r = None
        val error = Error(401, "token.invalid", None)
      }

      case object ExpiredAccessToken extends CommandError {
        val r = None
        val error = Error(401, "token.expired", None)
      }

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

    /**
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

    case class UserInfo(authType: AuthProvider, uid: UID, _r: Option[R], data: JsObject = Json.obj())

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

    case class RequestContext(tag: Tag, auth: UserInfo, meta: RequestMetadata)

    case class RequestMetadata(traceId: Option[String], when: DateTime, host: String, protocol: RequestProtocol)

  }

}
