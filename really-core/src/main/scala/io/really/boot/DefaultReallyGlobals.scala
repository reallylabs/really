/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.boot

import java.util.concurrent.atomic.AtomicReference
import akka.actor._
import akka.contrib.pattern.{ DistributedPubSubExtension }
import _root_.io.really.gorilla._
import _root_.io.really.model.persistent.{ ModelRegistry, RequestRouter, PersistentModelStore }
import _root_.io.really.model.materializer.{ MaterializerSharding, CollectionViewMaterializer }
import reactivemongo.api.{ DefaultDB, MongoDriver }
import scala.collection.JavaConversions._

import scala.slick.driver.H2Driver.simple._
import akka.contrib.pattern.ClusterSharding
import _root_.io.really._
import _root_.io.really.defaults.{ DefaultReceptionist, DefaultRequestActor }
import _root_.io.really.model.{ CollectionSharding, CollectionActor }
import play.api.libs.json.JsObject
import _root_.io.really.quickSand.QuickSand

class DefaultReallyGlobals(override val config: ReallyConfig) extends ReallyGlobals {
  private val actorSystem_ = new AtomicReference[ActorSystem]
  private val receptionist_ = new AtomicReference[ActorRef]
  private val quickSand_ = new AtomicReference[QuickSand]
  private val modelRegistry_ = new AtomicReference[ActorRef]
  private val requestRouter_ = new AtomicReference[ActorRef]
  private val collectionActor_ = new AtomicReference[ActorRef]
  private val gorillaEventCenter_ = new AtomicReference[ActorRef]
  private val mongodbConntection_ = new AtomicReference[DefaultDB]
  private val subscriptionManager_ = new AtomicReference[ActorRef]
  private val mediator_ = new AtomicReference[ActorRef]
  private val materializer_ = new AtomicReference[ActorRef]
  private val persistentModelStore_ = new AtomicReference[ActorRef]

  override lazy val receptionist = receptionist_.get
  override lazy val actorSystem = actorSystem_.get
  override lazy val quickSand = quickSand_.get
  override lazy val modelRegistry = modelRegistry_.get
  override lazy val requestRouter = requestRouter_.get
  override lazy val collectionActor = collectionActor_.get
  override lazy val gorillaEventCenter = gorillaEventCenter_.get
  override lazy val mongodbConntection = mongodbConntection_.get
  override lazy val subscriptionManager = subscriptionManager_.get
  override lazy val mediator = mediator_.get
  override lazy val materializerView = materializer_.get
  override lazy val persistentModelStore = persistentModelStore_.get

  private val db = Database.forURL(config.EventLogStorage.databaseUrl, driver = config.EventLogStorage.driver)
  private val modelRegistryPersistentId = "model-registry-persistent"

  def requestProps(context: RequestContext, replyTo: ActorRef, body: JsObject): Props =
    Props(new DefaultRequestActor(context, replyTo, body))

  override val receptionistProps = Props(new DefaultReceptionist(this))
  override val modelRegistryProps = Props(new ModelRegistry(this, modelRegistryPersistentId))
  override val requestRouterProps = Props(new RequestRouter(this, modelRegistryPersistentId))
  override val collectionActorProps = Props(classOf[CollectionActor], this)

  implicit val session = db.createSession()
  GorillaEventCenter.initializeDB()

  override val gorillaEventCenterProps = Props(classOf[GorillaEventCenter], this, session)
  override val subscriptionManagerProps = Props(classOf[SubscriptionManager], this)
  override val materializerProps = Props(classOf[CollectionViewMaterializer], this)
  override val persistentModelStoreProps = Props(classOf[PersistentModelStore], this, modelRegistryPersistentId)

  override val readHandlerProps = ???
  override val readHandler = ???

  override def boot(): Unit = {
    actorSystem_.set(ActorSystem("Really", config.coreConfig))
    implicit val ec = actorSystem.dispatcher
    val mongoDriver = new MongoDriver
    val connection = mongoDriver.connection(config.Mongodb.servers)
    mongodbConntection_.set(connection(config.Mongodb.dbName))

    receptionist_.set(actorSystem.actorOf(receptionistProps, "requests"))
    quickSand_.set(new QuickSand(config, actorSystem))
    modelRegistry_.set(actorSystem.actorOf(modelRegistryProps, "model-registry"))
    requestRouter_.set(actorSystem.actorOf(requestRouterProps, "request-router"))
    persistentModelStore_.set(actorSystem.actorOf(persistentModelStoreProps, "persistent-model-store"))

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

    val materializerSharding = new MaterializerSharding(config)
    materializer_.set(ClusterSharding(actorSystem).start(
      typeName = "MaterializerView",
      entryProps = Some(materializerProps),
      idExtractor = materializerSharding.idExtractor,
      shardResolver = materializerSharding.shardResolver
    ))

    mediator_.set(DistributedPubSubExtension(actorSystem).mediator)

    subscriptionManager_.set(actorSystem.actorOf(subscriptionManagerProps, "subscription-manager"))
  }

  override def shutdown(): Unit = {
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }
}
