/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really
import akka.actor._

object TestActors {
  val echoActorProps = akka.testkit.TestActors.echoActorProps
  def reportingActorProps(reportee: ActorRef) = Props(new ReportingActor(reportee))
}

class ReportingActor(reportee: ActorRef) extends Actor {
  import ReportingActor._

  reportee ! Created(context.parent, self)

  def receive = {
    case msg => reportee ! Received(ReportingActor, sender())
  }
}
object ReportingActor {
  case class Created(parent: ActorRef, self: ActorRef)
  case class Received(msg: Any, sender: ActorRef)
}
