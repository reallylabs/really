package io.really.model

import io.really.ReallyConfig

trait ShardingConfig {
  this: ReallyConfig =>

  object Sharding {
    protected val sharding = coreConfig.getConfig("collection-actor")
    val maxShards = sharding.getInt("max-shards")
    val bucketsNumber = sharding.getLong("number-of-buckets")
  }

}