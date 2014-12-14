/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

trait RequestDelegateConfig {
  this: ReallyConfig =>

  object Request {
    protected val request = coreConfig.getConfig("request")
    val timeoutAfter = request.getDuration("timeout-after", TimeUnit.MILLISECONDS).milliseconds
  }

}
