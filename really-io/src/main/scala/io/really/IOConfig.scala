package io.really

trait IOConfig {
  this: ReallyConfig =>

    object IO {
      protected val io = really.getConfig("io")
      val port = io.getInt("port")
      val host = io.getString("host")
    }

    object Ssl {
      protected val ssl = really.getConfig("ssl")
      val keyStoreResource = ssl.getString("keyStoreResource")
      val passphrase = ssl.getString("passphrase")
    }
}
