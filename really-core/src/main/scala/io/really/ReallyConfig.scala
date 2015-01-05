/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import com.typesafe.config.{ Config, ConfigFactory, ConfigException }
import _root_.io.really.gorilla.EventLogStorageConfig
import com.typesafe.config.{ Config, ConfigFactory }
import _root_.io.really.gorilla.{ GorillaConfig, EventLogStorageConfig }
import _root_.io.really.model.{ MongodbConfig, CollectionActorConfig, ShardingConfig }
import java.nio.file._

class ReallyConfig(config: Config) extends QuickSandConfig with ShardingConfig with CollectionActorConfig
    with MongodbConfig with EventLogStorageConfig with RequestDelegateConfig with GorillaConfig {
  protected val reference = ConfigFactory.defaultReference()

  private def newPath(file: String): Path = FileSystems.getDefault.getPath(file)

  protected val reallyConfig = config.getConfig("really") withFallback (reference.getConfig("really"))
  // validate against the reference configuration
  reallyConfig.checkValid(reference, "core")

  val coreConfig = reallyConfig.getConfig("core")

  def getRawConfig: Config = config

  def getReallyHome: Path = {
    try {
      val directory = config.getString("really.home")
      val path = newPath(directory)
      if (Files.notExists(path)) {
        throw new ReallyConfigException(s"really.home ($path) specified does not exist.")
      }
      if (!Files.isDirectory(path)) {
        throw new ReallyConfigException(s"really.home ($path) is not a directory.")
      }
      if (!Files.isReadable(path)) {
        throw new ReallyConfigException(s"really.home ($path) is not readable (permissions).")
      }
      path.toRealPath()
    } catch {
      case e: ConfigException.Missing =>
        //create the .really folder under current directory if needed
        val directory = System.getProperty("user.dir") + FileSystems.getDefault.getSeparator + ".really"
        val path = newPath(directory)
        if (Files.notExists(path)) {
          //let's create it.
          Files.createDirectory(path)
        }
        path.toRealPath()
    }

  }

  def getModelsPath: Path = getReallyHome.resolve("models")

}
