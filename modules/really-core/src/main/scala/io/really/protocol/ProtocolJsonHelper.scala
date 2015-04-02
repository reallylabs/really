/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.protocol

import _root_.io.really.Result._
import akka.actor.ActorRef
import io.really._
import _root_.io.really.rql.RQL
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Writes._

/*
 * JSON Formats for Really Protocol
 */

object ProtocolFormats {
  private val Body = "body"
  private val Tag = "tag"
  private val R = "r"
  private val Revision = "rev"
  private val Meta = "meta"
  private val Fields = "fields"
  private val Event = "evt"
  private val OpBy = "opBy"

  /*
   * JSON Reads for Request
   */
  object RequestReads {
    val tagReads = (__ \ 'tag).read[Long]
    val traceIdReads = (__ \ 'traceId).readNullable[String]
    val cmdReads = (__ \ 'cmd).read[String].map(_.toLowerCase)
    val accessTokenReads = (__ \ 'accessToken).read[String]
    val rReads = (__ \ 'r).read[R]
    val rObjectReads = rReads.filter(ValidationError("validate.error.require.object"))(_.isObject)
    val rCollectionReads = rReads.filter(ValidationError("validate.error.require.collection"))(_.isCollection)
    val revReads = (__ \ 'rev).read[Long]

    /*
     * JSON Reads for [[io.really.Request.Subscribe]] Request
     */
    object Subscribe {
      val bodyReads = (__ \ 'body).read[SubscriptionBody]

      def read(ctx: RequestContext, replyTo: ActorRef) = (bodyReads) map (body => Request.SubscribeOnObjects(ctx, body, replyTo))
    }

    /*
     * JSON Reads for [[io.really.Request.Unsubscribe]] Request
     */
    object Unsubscribe {
      val bodyReads = (__ \ 'body).read[UnsubscriptionBody]

      def read(ctx: RequestContext, replyTo: ActorRef) = (bodyReads) map (body => Request.UnsubscribeFromObjects(ctx, body, replyTo))
    }

    /*
     * JSON Reads for [[io.really.Request.Get]] Request
     */
    object Get {
      val cmdOptsReads = (__ \ 'cmdOpts).readNullable[GetOpts].defaultsTo(GetOpts())
      def read(ctx: RequestContext, replyTo: ActorRef) = (rObjectReads and cmdOptsReads)((r, cmdOpts) => Request.Get(ctx, r, cmdOpts))
    }

    /*
     * JSON Reads for [[io.really.Request.Update]] Request
     */
    object Update {
      val bodyReads = (__ \ 'body).read[UpdateBody]

      def read(ctx: RequestContext, replyTo: ActorRef) =
        (rObjectReads and revReads and bodyReads)((r, rev, body) => Request.Update(ctx, r, rev, body))
    }

    /**
     * JSON Reads for [[_root_.io.really.Request.Read]] Request
     */
    object Read {
      val cmdOptsReads = (__ \ 'cmdOpts).readNullable[ReadOpts].defaultsTo(ReadOpts(Set.empty, RQL.EmptyQuery, DEFAULT_QUERY_LIMIT)) //todo: d
      def read(ctx: RequestContext, replyTo: ActorRef) = (rCollectionReads and cmdOptsReads)((r, cmdOpts) => Request.Read(ctx, r, cmdOpts, replyTo))
    }

    /*
     * JSON Reads for [[io.really.Request.Create]] Request
     */
    object Create {
      val bodyReads = (__ \ 'body).read[JsObject]

      def read(ctx: RequestContext, replyTo: ActorRef) =
        (rCollectionReads and bodyReads)((r, body) => Request.Create(ctx, r, body))
    }

    /*
     * JSON Reads for [[io.really.Request.Delete]] Request
     */
    object Delete {
      def read(ctx: RequestContext, replyTo: ActorRef) = (rObjectReads) map (r => Request.Delete(ctx, r))
    }

    def apply(cmd: String) = scala.util.Try(cmd match {
      case "create" => Create.read _
      case "get" => Get.read _
      case "read" => Read.read _
      case "update" => Update.read _
      case "subscribe" => Subscribe.read _
      case "unsubscribe" => Unsubscribe.read _
      case "delete" => Delete.read _
    })

  }

  /*
   * Represent JSON Writes for Responses
   */
  object ResponseWrites {

    /*
     * Represent JSON Writes for Subscribe Response
     */
    implicit val subscribeResultWrites = new Writes[SubscribeResult] {
      def writes(r: SubscribeResult): JsValue =
        Json.obj(Body -> Json.obj("subscriptions" -> r.subscriptions))
    }

    /*
    * Represent JSON Writes for Unsubscribe Response
    */
    implicit val unsubscribeResultWrites = new Writes[UnsubscribeResult] {
      def writes(r: UnsubscribeResult): JsValue =
        Json.obj(Body -> Json.obj("unsubscriptions" -> r.unsubscriptions))
    }

    /*
     * Represent JSON Writes for [[io.really.Response.Get]] Response
     */
    implicit val getResultWrites = (
      (__ \ R).write[R] and
      (__ \ Body).write[JsObject] and
      (__ \ Meta \ Fields).write[Set[String]]
    )(unlift(GetResult.unapply))

    /*
     * Represent JSON Writes for [[io.really.Response.Update]] Response
     */
    implicit val updateResultWrites = (
      (__ \ R).write[R] and
      (__ \ Revision).write[Revision]
    )(unlift(UpdateResult.unapply))

    /*
     * Represent JSON Writes for [[io.really.Response.Read]] Response
     */
    implicit val readResultWrites = (
      (__ \ R).write[R] and
      (__ \ Body).write[ReadResponseBody] and
      (__ \ Meta \ "subscription").writeNullable[String]
    )(unlift(ReadResult.unapply))

    /*
     * Represent JSON Writes for [[io.really.Response.Create]] Response
     */
    implicit val createResultWrites = (
      (__ \ R).write[R] and
      (__ \ Body).write[JsObject]
    )(unlift(CreateResult.unapply))

    /*
     * Represent JSON Writes for [[io.really.Response.Delete]] Response
     */
    implicit val deleteWrites = new Writes[DeleteResult] {
      def writes(result: DeleteResult): JsValue =
        Json.obj(R -> result.r)
    }

    /*
     * Represent JSON Writes for [[io.really.Response]]
     * may generate different schema, depending on the concrete type
     */
    implicit val resultWrites = new Writes[Result] {
      def writes(r: Result): JsValue = r match {
        case response: SubscribeResult =>
          Json.toJson(response)
        case response: UnsubscribeResult =>
          Json.toJson(response)
        case response: GetResult =>
          Json.toJson(response)
        case response: UpdateResult =>
          Json.toJson(response)
        case response: ReadResult =>
          Json.toJson(response)
        case response: CreateResult =>
          Json.toJson(response)
        case response: DeleteResult =>
          Json.toJson(response)
      }
    }
  }

  /*
   * Represent JSON Writes for CommandErrors
   */
  object CommandErrorWrites {
    implicit val commandErrorWrites = (
      (__ \ "r").writeNullable[R] and
      (__ \ "error").write[ProtocolError.Error]
    )(unlift(CommandError.unapply))
  }

  /*
   * Represent JSON Writes for Push Messages
   */
  object PushMessageWrites {

    /*
     * Represent Created Push Message
     */
    object Created {
      def toJson(r: R, createdObj: JsObject) =
        Json.obj(
          R -> r,
          Event -> "created",
          Body -> createdObj
        )
    }

    /*
     * Represent Deleted Push Message
     */
    object Deleted {
      def toJson(r: R, userInfo: UserInfo) =
        Json.obj(
          R -> r,
          Event -> "deleted",
          Meta -> Json.obj(
            OpBy -> userInfo
          )
        )
    }

    /*
     * Represent Updated Push Message
     */
    object Updated {
      def toJson(r: R, rev: Revision, ops: List[FieldUpdatedOp], userInfo: UserInfo) =
        Json.obj(
          R -> r,
          Revision -> rev,
          Event -> "updated",
          Body -> Json.toJson(ops)(FieldUpdatedOp.FieldUpdatedOpListWrites),
          Meta -> Json.obj(
            OpBy -> userInfo
          )
        )
    }

  }

}
