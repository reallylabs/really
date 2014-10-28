/**
 * Copyright (C) 2014 Really Inc. <http://really.io>
 */

package io.really

package object io {
  trait IOConfig extends {
    this: ReallyConfig =>

    reallyConfig.checkValid(reference, "io")
    val ioConfig = reallyConfig.getConfig("io")
  }

}
