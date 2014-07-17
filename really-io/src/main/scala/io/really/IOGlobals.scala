package io.really

import javax.net.ssl.SSLContext
import spray.io.ServerSSLEngineProvider

trait ReallyIOGlobals extends ReallyGlobals {
  override def config: ReallyConfig with IOConfig
  implicit def sslContext: SSLContext
  implicit def sslEngineProvider: ServerSSLEngineProvider
}
