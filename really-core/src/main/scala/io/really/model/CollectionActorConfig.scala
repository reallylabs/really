/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import _root_.io.really.ReallyConfig

trait CollectionActorConfig {
  this: ReallyConfig =>

  object CollectionActorConfig {
    protected val collectionActor = coreConfig.getConfig("collection-actor")
    val waitForModel = collectionActor.getDuration("wait-for-model", TimeUnit.MILLISECONDS).milliseconds
    val waitForObjectState = collectionActor.getDuration("wait-for-object-state", TimeUnit.MILLISECONDS).milliseconds
    val idleTimeout = collectionActor.getDuration("idle-timeout", TimeUnit.MILLISECONDS).milliseconds
  }

}
