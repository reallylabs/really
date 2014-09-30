package io.really.boot

import java.util.concurrent.atomic.AtomicReference
import akka.actor._
import akka.contrib.pattern.ClusterSharding
import io.really._
import io.really.defaults.{DefaultReceptionist, DefaultRequestActor}
import io.really.model.{ModelRegistryRouter, CollectionSharding, CollectionActor}
import play.api.libs.json.JsObject
import io.really.quickSand.QuickSand

class DefaultReallyGlobals(override val config: ReallyConfig) extends ReallyGlobals {
  private val actorSystem_ = new AtomicReference[ActorSystem]
  private val receptionist_ = new AtomicReference[ActorRef]
  private val quickSand_ = new AtomicReference[QuickSand]
  private val modelRegistryRouter_ = new AtomicReference[ActorRef]
  private val collectionActor_ = new AtomicReference[ActorRef]

  override lazy val receptionist = receptionist_.get
  override lazy val actorSystem = actorSystem_.get
  override lazy val quickSand = quickSand_.get
  override lazy val modelRegistryRouter = modelRegistryRouter_.get
  override lazy val collectionActor = collectionActor_.get

  def requestProps(context: RequestContext, replyTo: ActorRef, body: JsObject): Props = Props(new DefaultRequestActor(context, replyTo, body))

  //todo this should be dynamically loaded from configuration
  override val receptionistProps = Props(new DefaultReceptionist(this))
  override val modelRegistryRouterProps = Props(new ModelRegistryRouter(this))
  override val collectionActorProps = Props(classOf[CollectionActor], this)

  override def boot() = {
    actorSystem_.set(ActorSystem("Really", config.coreConfig))
    receptionist_.set(actorSystem.actorOf(receptionistProps, "requests"))
    quickSand_.set(new QuickSand(config, actorSystem))
    modelRegistryRouter_.set(actorSystem.actorOf(modelRegistryRouterProps, "model-registry-router"))
    val collectionSharding = new CollectionSharding(config)
    collectionActor_.set(ClusterSharding(actorSystem).start(
      typeName = "CollectionActor",
      entryProps = Some(collectionActorProps),
      idExtractor = collectionSharding.idExtractor,
      shardResolver = collectionSharding.shardResolver))
  }
}
