/*
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.gorilla.mock

import io.really.ReallyGlobals
import io.really.gorilla.{ ObjectSubscriber, RSubscription }
import io.really.model.Model

class TestObjectSubscriber(rSubscription: RSubscription, globals: ReallyGlobals) extends ObjectSubscriber(rSubscription, globals) {

  import TestObjectSubscriber._

  override def withModel(model: Model): Receive = testOps orElse super.withModel(model)

  def testOps: Receive = {
    case GetFieldList =>
      sender() ! this.fields
    case Ping =>
      sender() ! Pong
  }
}

object TestObjectSubscriber {

  case object GetFieldList

  case object Ping

  case object Pong

}
