/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.gorilla

import io.really.ReallyConfig

trait EventLogStorageConfig {
  this: ReallyConfig =>

  object EventLogStorage {

    private val eventLogConfig = coreConfig.getConfig("event-log")

    val databaseUrl = eventLogConfig.getString("database-url")

    val driver = eventLogConfig.getString("driver")

  }

}