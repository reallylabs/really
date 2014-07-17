package io.really.defaults

import akka.actor._
import io.really._

/**
 * Receives a [[Request]] instance and a requester [[ActorRef]] and instantiates a RequestActor
 * that serves this request and dies upon finalising the request
 *
 * The Receptionist is a single actor per machine and is restarted on death (based on default
 * supervision strategy)
 *
 */

class DefaultReceptionist(globals: ReallyGlobals) extends Receptionist(globals) {

  def receive = Actor.emptyBehavior
}