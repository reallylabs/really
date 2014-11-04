/**
 * Copyright (C) 2014 Really Inc. <http://really.io>
 */

package io.really.io

import java.util.concurrent.atomic.AtomicReference

import io.really.io.boot.DefaultIOConfig
import io.really.ReallyGlobals
import io.really.boot.DefaultReallyGlobals
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
  }

  override def onStop(app: Application) {
    ioGlobals.shutdown()
    coreGlobals.shutdown()
    Logger.info("Have a nice day...")
  }
}