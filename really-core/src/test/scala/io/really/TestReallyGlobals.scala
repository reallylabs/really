/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.contrib.pattern.ClusterSharding
import io.really.defaults.{DefaultRequestActor, DefaultReceptionist}
import io.really.model.{ModelRegistryRouter, CollectionActor, CollectionSharding}
import io.really.quickSand.QuickSand
import play.api.libs.json.JsObject

class TestReallyGlobals(override val config: ReallyConfig, override val actorSystem: ActorSystem) extends ReallyGlobals {
  protected val receptionist_ = new AtomicReference[ActorRef]
  protected val quickSand_ = new AtomicReference[QuickSand]
  protected val modelRegistry_ = new AtomicReference[ActorRef]
  protected val collectionActor_ = new AtomicReference[ActorRef]

  override lazy val receptionist = receptionist_.get
  override lazy val quickSand = quickSand_.get
  override lazy val modelRegistryRouter = modelRegistry_.get
  override lazy val collectionActor = collectionActor_.get

  def requestProps(context: RequestContext, replyTo: ActorRef, body: JsObject): Props =
    Props(new DefaultRequestActor(context, replyTo, body))

  //todo this should be dynamically loaded from configuration
  override val receptionistProps = Props(new DefaultReceptionist(this))
  override val modelRegistryRouterProps = Props(new ModelRegistryRouter(this))
  override val collectionActorProps = Props(classOf[CollectionActor], this)

  override def boot() = {
    receptionist_.set(actorSystem.actorOf(receptionistProps, "requests"))
    quickSand_.set(new QuickSand(config, actorSystem))
    modelRegistry_.set(actorSystem.actorOf(modelRegistryRouterProps, "model-registry"))
    val collectionSharding = new CollectionSharding(config)
    collectionActor_.set(ClusterSharding(actorSystem).start(
      typeName = "CollectionActor",
      entryProps = Some(collectionActorProps),
      idExtractor = collectionSharding.idExtractor,
      shardResolver = collectionSharding.shardResolver))
  }

  override def shutdown() = {
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }
}
