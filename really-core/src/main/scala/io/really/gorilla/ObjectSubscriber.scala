/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla

import akka.actor._
import io.really.gorilla.GorillaEventCenter.ReplayerSubscribed
import io.really.gorilla.SubscriptionManager.{ UpdateSubscriptionFields, Unsubscribe }
import io.really.ReallyGlobals
import io.really.model.persistent.ModelRegistry
import io.really.model.persistent.ModelRegistry.RequestModel.GetModel
import io.really.model.persistent.ModelRegistry.ModelResult
import io.really.model.persistent.ModelRegistry.ModelResult.ModelFetchError
import io.really.protocol.FieldUpdatedOp
import io.really.protocol.ProtocolFormats.PushMessageWrites.{ Updated, Deleted }
import scala.concurrent.ExecutionContext.Implicits.global
import io.really.model.Model
import io.really.protocol.SubscriptionFailure
import io.really.protocol.SubscriptionFailure.SubscriptionFailureWrites

/**
 * ObjectSubscriber Actor is responsible for receiving the object updates, filter them before pushing to the user by
 * evaluating the Model's onGet JS script
 * @param rSubscription Represents the subscription request data
 * @param globals System globals singleton
 */
class ObjectSubscriber(rSubscription: RSubscription, globals: ReallyGlobals) extends Actor with ActorLogging
    with Stash {

  val r = rSubscription.r
  private[gorilla] val logTag = s"ObjectSubscriber ${rSubscription.pushChannel.path}$$$r"

  private[gorilla] var fields = rSubscription.fields

  val shotgun = context.system.scheduler.scheduleOnce(globals.config.GorillaConfig.waitForReplayer, self, PoisonPill)

  def subscriptionFailed(errorCode: Int, reason: String) = {
    log.error(s"$logTag is going to die since the subscription failed because of: $reason\n error code: $errorCode")
    rSubscription.pushChannel ! SubscriptionFailureWrites.writes(SubscriptionFailure(r, errorCode, "Internal Server Error"))
    context.stop(self)
  }

  def commonHandler: Receive = {
    case Unsubscribe =>
      log.debug(s"$logTag is going to die since it got an unsubscribe request")
      rSubscription.requestDelegate ! Unsubscribe
      context.stop(self)
    case SubscriptionFailure(r, errorCode, reason) =>
      context.unwatch(sender())
      subscriptionFailed(errorCode, "Internal Server Error")
    case Terminated(actor) =>
      subscriptionFailed(505, "Associated replayer stopped")
  }

  def receive: Receive = commonHandler orElse waitingModel

  def waitingModel: Receive = {
    case ReplayerSubscribed(replayer) =>
      context.watch(replayer)
      globals.modelRegistry ! GetModel(rSubscription.r, self)
      shotgun.cancel()
    case evt @ ModelResult.ModelObject(m, _) =>
      if (!fields.isEmpty && m.fields.keySet.intersect(fields) == Set.empty) {
        //This means that not a field in the subscription list relates to this model fields
        subscriptionFailed(506, "Irrelevant subscription fields")
      } else if (fields.isEmpty) {
        fields = evt.model.fields.keySet
      }
      unstashAll()
      context.become(withModel(m) orElse commonHandler)
    case ModelResult.ModelNotFound =>
      subscriptionFailed(503, s"Couldn't find the model for r: $r")
    case ModelFetchError(r, reason) =>
      subscriptionFailed(504, s"Couldn't fetch the model for r: $r because of: $reason")
    case _ =>
      stash()
  }

  def withModel(model: Model): Receive = {
    case entry: GorillaLogUpdatedEntry =>
      if (entry.modelVersion == model.collectionMeta.version) {
        model.executeOnGet(rSubscription.ctx, globals, entry.obj) match {
          case Right(plan) =>
            val interestFields = fields -- plan.hidden
            val updatedFields = entry.ops.filter(op => interestFields.contains(op.key)).map {
              op =>
                FieldUpdatedOp(op.key, op.op, Some(op.value))
            }
            rSubscription.pushChannel ! Updated.toJson(r, entry.rev, updatedFields, entry.userInfo)
          case Left(terminated) =>
        }
      } else {
        subscriptionFailed(502, "Model Version inconsistency")
      }
    case entry: GorillaLogDeletedEntry =>
      rSubscription.pushChannel ! Deleted.toJson(r, entry.userInfo)
      context.stop(self)
    case UpdateSubscriptionFields(newFields) =>
      if (newFields.isEmpty) {
        fields = model.fields.keySet
      } else {
        fields = fields union Set(newFields.toSeq: _*)
      }
    case ModelRegistry.ModelOperation.ModelUpdated(_, newModel, _) =>
      context.become(withModel(newModel) orElse commonHandler)
    case ModelRegistry.ModelOperation.ModelDeleted(deletedR) if deletedR.skeleton == r.skeleton =>
      subscriptionFailed(501, s"received a DeletedModel message for: $r")
      context.stop(self)
  }
}