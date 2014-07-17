package io.really

import com.typesafe.config.{Config, ConfigFactory}
import io.really.model.PersistenceConfig
import io.really.quickSand.QuickSandConfig

class ReallyConfig(val config: Config) extends QuickSandConfig with PersistenceConfig {
  config.checkValid(ConfigFactory.defaultReference(), "really")
  val akkaConfig = config.getConfig("akka")
  protected val really = config.getConfig("really")
}
