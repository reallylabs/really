/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem

class IOGlobals(val config: IOConfig) {
  import play.api.Logger
  private[this] val _actorSystem = new AtomicReference[ActorSystem]
  private val logger = Logger(getClass)
  lazy val actorSystem = _actorSystem.get

  def boot(): Unit = {
    _actorSystem.set(ActorSystem("IO", config.ioConfig))
    logger.info("IO Booted")
  }

  def shutdown(): Unit = {
    actorSystem.shutdown()
    actorSystem.awaitTermination()
    logger.info("IO Terminated")
  }
}