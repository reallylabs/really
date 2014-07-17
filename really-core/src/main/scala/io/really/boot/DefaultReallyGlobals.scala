package io.really.boot

import java.util.concurrent.atomic.AtomicReference
import akka.actor._
import io.really._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import io.really.defaults.{DefaultReceptionist, DefaultRequestActor}
import play.api.libs.json.JsObject
import io.really.quickSand.QuickSand

class DefaultReallyGlobals(override val config: ReallyConfig) extends ReallyGlobals {
  private val actorSystem_ = new AtomicReference[ActorSystem]
  private val receptionist_ = new AtomicReference[ActorRef]
  private val quickSand_ = new AtomicReference[QuickSand]
  private val modelRegistry_ = new AtomicReference[ActorRef]

  override lazy val receptionist = receptionist_.get
  override lazy val actorSystem = actorSystem_.get
  override lazy val quickSand = quickSand_.get
  override lazy val modelRegistry = modelRegistry_.get

  def requestProps(context: RequestContext, replyTo: ActorRef, body: JsObject): Props = Props(new DefaultRequestActor(context, replyTo, body))
  //todo this should be dynamically loaded from configuration
  override val receptionistProps = Props(new DefaultReceptionist(this))
  override val modelRegistryProps = Props.empty //todo: (new SimpleModelRegistry(this))

  override def boot() = {
    actorSystem_.set(ActorSystem("ReallyCore", config.akkaConfig))
    receptionist_.set(actorSystem.actorOf(receptionistProps, "requests"))
    quickSand_.set(new QuickSand(config, actorSystem))
    modelRegistry_.set(actorSystem.actorOf(modelRegistryProps, "model-registry"))

  }
}
