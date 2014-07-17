package io.really.jshooks

import io.really.model.ModelHookStatus.ValidationError
import org.scalatest.{FlatSpec, Matchers}

class APISpec extends FlatSpec with Matchers{
  "cancel" should "accept error code as number, error message as string and throw a ValidationError" in {
    intercept[ValidationError] {
      API.cancel.accept(401, "Unauthorized")
    }
  }
}
