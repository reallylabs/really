package io.really.views

import io.really._
import akka.actor._
import akka.persistence._

class ObjectView(r: R) extends PersistentView with ActorLogging {
  override def persistenceId = r.toString

  override val viewId = r.toString + "-simple-view"

  def receive = {
    case payload => Unit
  }
}