/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io

class IOGlobals(val config: IOConfig) {
  import play.api.Logger
  private val logger = Logger(getClass)

  def boot(): Unit = {
    logger.info("IO Booted")
  }

  def shutdown(): Unit = {
    logger.info("IO Terminated")
  }
}