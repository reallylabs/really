/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really
import akka.actor._
import akka.persistence.PersistentActor

object TestActors {
  val echoActorProps = akka.testkit.TestActors.echoActorProps

  def reportingActorProps(reportee: ActorRef) =
    Props(new ReportingActor(reportee))

  def reportingPersistentActorProps(persistenceId: String, reportee: ActorRef) =
    Props(new ReportingPersistentActor(persistenceId, reportee))
}

class ReportingActor(reportee: ActorRef) extends Actor {
  import ReportingActor._

  reportee ! Created(context.parent, self)

  def receive = {
    case msg => reportee ! Received(msg, sender())
  }
}
object ReportingActor {
  case class Created(parent: ActorRef, self: ActorRef)
  case class Received(msg: Any, sender: ActorRef)
}

class ReportingPersistentActor(override val persistenceId: String, reportee: ActorRef) extends PersistentActor {
  import ReportingPersistentActor._

  val receiveRecover: Receive = {
    case msg =>
      reportee ! ReceivedRecover(msg, sender())
  }

  val receiveCommand: Receive = {
    case Persist(event) =>
      persist(event) { event =>
        reportee ! Persisted(event)
      }
    case msg =>
      reportee ! ReceivedCommand(msg, sender())
  }

}
object ReportingPersistentActor {
  case class ReceivedRecover(msg: Any, sender: ActorRef)
  case class ReceivedCommand(msg: Any, sender: ActorRef)
  case class Persist(event: Any)
  case class Persisted(event: Any)
}

