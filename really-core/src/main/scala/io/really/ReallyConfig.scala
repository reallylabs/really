package io.really

import com.typesafe.config.{Config, ConfigFactory}
import io.really.model.{CollectionActorConfig, ShardingConfig}
import io.really.quickSand.QuickSandConfig

class ReallyConfig(config: Config) extends QuickSandConfig with ShardingConfig
with CollectionActorConfig {
  protected val reference = ConfigFactory.defaultReference()

  protected val reallyConfig = config.getConfig("really") withFallback(reference.getConfig("really"))
  // validate against the reference configuration
  reallyConfig.checkValid(reference, "core")

  val coreConfig = reallyConfig.getConfig("core")
  def getRawConfig(): Config = config
}
