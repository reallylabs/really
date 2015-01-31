/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import _root_.io.really.ReallyConfig

trait MaterializerActorConfig {
  this: ReallyConfig =>

  object MaterializerActorConfig {
    protected val materializerActor = coreConfig.getConfig("materializer-actor")
    val waitForModel = materializerActor.getDuration("wait-for-db", TimeUnit.MILLISECONDS).milliseconds
  }

}
