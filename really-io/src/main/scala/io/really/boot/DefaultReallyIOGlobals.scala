package io.really.boot

import java.util.concurrent.atomic.AtomicReference
import akka.actor._
import io.really._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import io.really.defaults.{DefaultReceptionist, DefaultRequestActor}
import play.api.libs.json.JsObject
import io.really.quickSand.QuickSand

import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import spray.io._

class DefaultReallyIOGlobals(override val config: ReallyConfig with IOConfig) extends DefaultReallyGlobals(config) with ReallyIOGlobals {
  private lazy val sslConf = new SslConfiguration(config)
  implicit lazy val sslContext: SSLContext = sslConf.sslContext
  implicit lazy val sslEngineProvider: ServerSSLEngineProvider = sslConf.sslEngineProvider

  override def boot() = {
    super.boot()
    // Custom initialization code here
  }
}

/*
 * SSL Support
 * To enable SSL support:
 * 1. Must be enabled in configuration by setting
 * {{{
 * spray.can.server.ssl-encryption = on
 * }}}
 * 2. Make sure you installed Unlimited JCE policy
 * Java8: http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html
 * Java7: http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html
 * Steps: http://stackoverflow.com/a/25453855
 *
 * Note to developers: if implicits are not in scope, the HttpServer uses it's default.
 * Make sure that implicits defined are this object is available in Main script (HttpServer scope)
 * {{{
 * import globals._
 * }}}
 */
class SslConfiguration(config: IOConfig) {

  // if there is no SSLContext in scope implicitly the HttpServer uses the default SSLContext,
  // since we want non-default settings in this example we make a custom SSLContext available here
  implicit val sslContext: SSLContext = {
    val keyStoreResource = config.Ssl.keyStoreResource
    val passphrase = config.Ssl.passphrase

    val keyStore = KeyStore.getInstance("jks")
    keyStore.load(getClass.getResourceAsStream(keyStoreResource), passphrase.toCharArray)
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, passphrase.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  // if there is no ServerSSLEngineProvider in scope implicitly the HttpServer uses the default one,
  // since we want to explicitly enable cipher suites and protocols we make a custom ServerSSLEngineProvider
  // available here
  implicit val sslEngineProvider: ServerSSLEngineProvider =
    ServerSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }

}
