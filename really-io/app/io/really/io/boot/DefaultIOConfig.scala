/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io.boot

import com.typesafe.config.Config
import io.really.ReallyConfig
import io.really.io.IOConfig

class DefaultIOConfig(config: Config) extends ReallyConfig(config) with IOConfig