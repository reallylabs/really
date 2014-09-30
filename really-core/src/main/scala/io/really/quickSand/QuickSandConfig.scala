package io.really.quickSand

import io.really.ReallyConfig

trait QuickSandConfig {
  this: ReallyConfig =>

    object QuickSand {
      protected val quicksand = coreConfig.getConfig("quicksand")
      val workerId = quicksand.getLong("workerId")
      val datacenterId = quicksand.getLong("datacenterId")
      val reallyEpoch: Long = quicksand.getLong("epoch")
    }
}
