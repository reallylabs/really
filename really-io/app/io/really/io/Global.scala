/**
 * Copyright (C) 2014 Really Inc. <http://really.io>
 */

package io.really.io

import java.util.concurrent.atomic.AtomicReference

import io.really.io.boot.DefaultIOConfig
import io.really.ReallyGlobals
import io.really.boot.DefaultReallyGlobals
import io.really.model.loader.ModelLoader
import io.really.model.persistent.PersistentModelStore.UpdateModels
import play.api._

object Global extends GlobalSettings {
  private[this] val _reallyConfig = new AtomicReference[DefaultIOConfig]
  private[this] val _coreGlobals = new AtomicReference[ReallyGlobals]
  private[this] val _ioGlobals = new AtomicReference[IOGlobals]

  lazy val coreGlobals = _coreGlobals.get
  lazy val ioGlobals = _ioGlobals.get
  lazy val reallyConfig = _reallyConfig.get

  override def onStart(app: Application) {
    _reallyConfig.set(new DefaultIOConfig(app.configuration.underlying))
    val core = new DefaultReallyGlobals(_reallyConfig.get)
    core.boot()
    _coreGlobals.set(core)
    val io = new IOGlobals(_reallyConfig.get)
    io.boot()
    _ioGlobals.set(io)

    println(
      """
        |                                ▄▄▄▄▓▓▓▓▓▓▓▓▓▓▓▓▓▓▄▄▄▄▄
        |                         ▄▄▄▓▓▓▓██▀▀▀▀           ▀▀▀██▓▓▓▓▄▄
        |                     ▄▄▓▓▓█▀▀                            ▀██▓▓▓▄
        |                  ▄▓▓▓█▀                                     ▀█▓▓▓▄
        |               ▄▓▓▓█▀                                           ▀█▓▓▄
        |             ▄▓▓█▀                                                 █▓▓▄
        |            ▓▓█▀                                                    ▀▓▓▄
        |          ▄▓▓▀                                                        ▓▓▓
        |         ▓▓▓                                                           ▓▓▄
        |        ▓▓▓                                                            ▐▓▓
        |       ▄▓▓                                   ▄▓▓▓▄                      █▓▓
        |      ▐▓▓▀                                 ▄▓▓▄ ▐▓▓                  ▓▓▄ ▓▓
        |      ▓▓▓                                 ▄▓▓▓▓▓▒▓▓                  ▓▓▓▓▓▓▌
        |      ▓▓▌                                ▓▓▓▓▓▓▓░▓▓              ▄▄  ▓▓▓▀███
        |      ▓▓▌        ▐▓ ▄   ░              ▄▓▓▓▓▓▓█▄▓▓▀              ▓▓▓▄▓▓▓
        |      ▓▓▌         █▓▓   ▓▄ ▒░ ▒▄▄     ▄▓▓▀▀██▓▓▓▓                ▐▓▓▓▓▓▓
        |      ▐▓▓▄▒▓▄      ▀▓▓  ▀▓▄▓░ ▀▓▓    ▄▓▓▓▄▄▄▓▓█▀               ▓▓▓▓▓▓▀▀█▓
        |       ▀▓▓░▓▓▓▓▄          █▓▌   ▀▒  ▐█████▀▀                   ▓▓▓▓▓▓▓▄
        |        ▀▓▓▄█▓▓▓▓▄         ▀█▌                                 ▒▓▓ ▀▀██▓▄
        |          █▓▓▄ ▀▀▀        ▄▄▄▄  ▄▄▄▄▄        ▄▄▓▓▌     ▀▓▄▄▄    █▓▓
        |           ▀▓▓▌       ▄▄ ▐▓▀▀▀▀▀▓▓▓▓▓▄▄▄▄▄▄▓█▀▒▓▀        ▀▓▓▓▓▄▄▄▓▓▓
        |           ▒▓▓       ▓▓▓▓█▓▄▄▄▄▄▓▓▓▀░▓▓▓▓▓▀▄▓▓▓▄▄▄▄▄▄▄▄▄▄▄▄▓▓▓████▓▓▓
        |          ▐▓▓▓▄▄     ▐▓▓▓▓▓▓▀▀██▓▓▓▓▓▓▓▓█▓▓▀▀██████▓▓▓▓▀▀██▓▓▓▓
        |          ▀▀▀▀▓▓▓▄▄  ▀▓▄▄▓▓▀      ▀▀ ▓▓▌▄▓       ▄▄▓▓▓▓▓▓▄▄ ▀██
        |              ▓▓▓▓▓▌   ▓▓▓           ▓▓▓▓▀       ▄▓▓▓▓▓▓▓▓▀▀▀
        |              ▀▓▓▓▓▓  ▄▓▓▓▓          ▓▓▓▀       ▄▄▀▓▓▓▓▓▓▓▓▓▄
        |               ▀█ ▀▓▓▄▓▓▓▓▓▌         ▓▓▀       ▐▓▓▓▓▓▓▓▄
        |                   █▓▓▓▓▓▀▓▓▄       ▐▓▓      ░▓ █▓▓██▓▓▓▓▓▄
        |                    ▀▓▓▓▓▄▄▓▓▄     ▄▓▓▀  ▄▓▄ ▐▓▓▄▓▓▓▄
        |                     ▀▀▓▓▓▓▓▓▓▓   ▄▓▓▀▒▄ ▓▓▓▓▓▓▓▓▓▓▓▓▓▄
        |                       ▐▓▓▓▓▓▓▓▓▄ ▓▓▌ ▓▓▄░▓▓▀██▓▓  ▀▀▀▀▀
        |                        ▀▓▓▓▓▓▓▓▓▓▓▓▌ ▓▓▓▓▓▓▓▄
        |                         ▀▓▓░▀█▓▓▓███▒▓▓▀▀██▓▓▓
        |                          ▀▓▓   █▓▓▄▄ ▓▓▌
        |                            ▀░    ▀█▓▓▓▓▓
        |                                     ▀▀▓▓
      """
    )

    Logger.info("Hello from Really!")

    // loading models
    val reallyHome = coreGlobals.config.getReallyHome
    Logger.info(s"(really.home) is ($reallyHome)")
    val modelsPath = coreGlobals.config.getModelsPath.toString
    Logger.info("Loading data models from " + modelsPath)
    val loader = new ModelLoader(modelsPath, core.actorSystem)
    Logger.info("models:" + loader.models)

    if (loader.models.nonEmpty) {
      Logger.info("Sending new models to " + core.persistentModelStore)

      core.persistentModelStore ! UpdateModels(loader.models)
    } else
      Logger.warn("No MODELS found, note that this doesn't mean that you don't have persistent models already that will be served.")

  }

  override def onStop(app: Application) {
    ioGlobals.shutdown()
    coreGlobals.shutdown()
    Logger.info("Have a nice day...")
  }
}