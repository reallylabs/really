/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ ActorSystem, Props, ActorRef }
import akka.contrib.pattern.{ DistributedPubSubExtension, ClusterSharding }
import _root_.io.really.defaults.{ DefaultRequestActor, DefaultReceptionist }
import _root_.io.really.gorilla.{ SubscriptionManager, GorillaEventCenterSharding, GorillaEventCenter }
import _root_.io.really.model.{ CollectionActor, CollectionSharding }
import _root_.io.really.quickSand.QuickSand
import _root_.io.really.model.persistent.{ ModelRegistry, RequestRouter }
import play.api.libs.json.JsObject
import reactivemongo.api.{ DefaultDB, MongoDriver }
import scala.collection.JavaConversions._
import scala.slick.driver.H2Driver.simple._

class TestReallyGlobals(override val config: ReallyConfig, override val actorSystem: ActorSystem) extends ReallyGlobals {
  protected val receptionist_ = new AtomicReference[ActorRef]
  protected val quickSand_ = new AtomicReference[QuickSand]
  protected val modelRegistry_ = new AtomicReference[ActorRef]
  protected val requestRouter_ = new AtomicReference[ActorRef]
  protected val collectionActor_ = new AtomicReference[ActorRef]
  protected val gorillaEventCenter_ = new AtomicReference[ActorRef]
  private val mongodbConntection_ = new AtomicReference[DefaultDB]
  private val subscriptionManager_ = new AtomicReference[ActorRef]
  private val mediator_ = new AtomicReference[ActorRef]

  override lazy val receptionist = receptionist_.get
  override lazy val quickSand = quickSand_.get
  override lazy val modelRegistry = modelRegistry_.get
  override lazy val requestRouter = requestRouter_.get
  override lazy val collectionActor = collectionActor_.get
  override lazy val gorillaEventCenter = gorillaEventCenter_.get
  override lazy val mongodbConntection = mongodbConntection_.get
  override lazy val subscriptionManager = subscriptionManager_.get
  override lazy val mediator = mediator_.get

  override val readHandlerProps = Props.empty //FIXME
  override val readHandler = actorSystem.deadLetters //FIXME

  private val db = Database.forURL(config.EventLogStorage.databaseUrl, driver = config.EventLogStorage.driver)

  def requestProps(context: RequestContext, replyTo: ActorRef, body: JsObject): Props =
    Props(new DefaultRequestActor(context, replyTo, body))

  //todo this should be dynamically loaded from configuration
  override val receptionistProps = Props(new DefaultReceptionist(this))
  override val modelRegistryProps = Props(new ModelRegistry(this))
  override val requestRouterProps = Props(new RequestRouter(this))
  override val collectionActorProps = Props(classOf[CollectionActor], this)

  implicit val session = db.createSession()
  GorillaEventCenter.initializeDB()

  override def gorillaEventCenterProps = Props(classOf[GorillaEventCenter], this, session)
  override val subscriptionManagerProps = Props(classOf[SubscriptionManager], this)

  override def boot() = {
    implicit val ec = actorSystem.dispatcher
    val driver = new MongoDriver
    val connection = driver.connection(config.Mongodb.servers)
    mongodbConntection_.set(connection(config.Mongodb.dbName))

    receptionist_.set(actorSystem.actorOf(receptionistProps, "requests"))
    quickSand_.set(new QuickSand(config, actorSystem))
    modelRegistry_.set(actorSystem.actorOf(modelRegistryProps, "model-registry"))
    requestRouter_.set(actorSystem.actorOf(requestRouterProps, "request-router"))
    val collectionSharding = new CollectionSharding(config)
    collectionActor_.set(ClusterSharding(actorSystem).start(
      typeName = "CollectionActor",
      entryProps = Some(collectionActorProps),
      idExtractor = collectionSharding.idExtractor,
      shardResolver = collectionSharding.shardResolver
    ))

    val gorillaEventCenterSharding = new GorillaEventCenterSharding(config)

    gorillaEventCenter_.set(ClusterSharding(actorSystem).start(
      typeName = "GorillaEventCenter",
      entryProps = Some(gorillaEventCenterProps),
      idExtractor = gorillaEventCenterSharding.idExtractor,
      shardResolver = gorillaEventCenterSharding.shardResolver
    ))

    mediator_.set(DistributedPubSubExtension(actorSystem).mediator)

    subscriptionManager_.set(actorSystem.actorOf(subscriptionManagerProps, "subscription-manager"))
  }

  override def shutdown() = {
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }
}
