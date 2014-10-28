/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.jshooks

import io.really.model.ModelHookStatus.JSValidationError
import org.scalatest.{FlatSpec, Matchers}

class APISpec extends FlatSpec with Matchers{
  "cancel" should "accept error code as number, error message as string and throw a ValidationError" in {
    intercept[JSValidationError] {
      API.cancel.accept(401, "Unauthorized")
    }
  }
}
