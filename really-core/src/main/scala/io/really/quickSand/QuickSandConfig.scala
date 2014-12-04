/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.quickSand

import _root_.io.really.ReallyConfig

trait QuickSandConfig {
  this: ReallyConfig =>

  object QuickSand {
    protected val quicksand = coreConfig.getConfig("quicksand")
    val workerId = quicksand.getLong("workerId")
    val datacenterId = quicksand.getLong("datacenterId")
    val reallyEpoch: Long = quicksand.getLong("epoch")
  }
}
