/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.token

package object generator {

  object AuthType extends Enumeration {

    type AuthType = Value
    val Anonymous, Password = Value
    def Custom(provider: String) = Value(provider)
  }

}
