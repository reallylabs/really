/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import akka.actor.{ Actor, ActorLogging }

abstract class Receptionist(global: ReallyGlobals) extends Actor with ActorLogging