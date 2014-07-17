package io.really

import akka.actor.{Actor, ActorLogging}

abstract class Receptionist(global: ReallyGlobals) extends Actor with ActorLogging