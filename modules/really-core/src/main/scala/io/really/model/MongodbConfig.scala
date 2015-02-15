/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really.ReallyConfig
import reactivemongo.core.nodeset.Authenticate

trait MongodbConfig {
  this: ReallyConfig =>

  object Mongodb {
    // gets an instance of the driver
    val mongodbConfig = coreConfig.getConfig("mongodb")
    val dbName = mongodbConfig.getString("dbName")
    val authenticate = Authenticate(dbName, mongodbConfig.getString("user"),
      mongodbConfig.getString("password"))
    val servers = mongodbConfig.getStringList("servers")
  }
}