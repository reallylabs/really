package io.really

trait IOConfig {
  this: ReallyConfig =>

    reallyConfig.checkValid(reference, "io")
    val ioConfig = reallyConfig.getConfig("io")

    object IO {
      val port = ioConfig.getInt("port")
      val host = ioConfig.getString("host")
    }

    object Ssl {
      protected val ssl = ioConfig.getConfig("ssl")
      val keyStoreResource = ssl.getString("key-store-resource")
      val passphrase = ssl.getString("passphrase")
    }
}
