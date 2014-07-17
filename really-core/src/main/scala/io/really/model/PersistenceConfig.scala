package io.really.model

import java.util.concurrent.TimeUnit

import io.really.ReallyConfig

import scala.concurrent.duration.Duration

trait PersistenceConfig {
  this: ReallyConfig =>

  object persistence {
    val objectActorTimeout = Duration(config.getDuration("really.persistence.object-persistor-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  }
}