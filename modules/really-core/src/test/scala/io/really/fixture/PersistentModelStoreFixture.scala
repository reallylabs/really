/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.fixture

import io.really.model.persistent.PersistentModelStore
import io.really.ReallyGlobals

class PersistentModelStoreFixture(globals: ReallyGlobals, persistId: String)
    extends PersistentModelStore(globals, persistId) {

  def testOps: Receive = {
    case msg @ PersistentModelStoreFixture.GetState =>
      sender ! state
  }

  override def receiveCommand: Receive = super.receiveCommand orElse testOps

}

object PersistentModelStoreFixture {
  case object GetState extends PersistentModelStore.Command
}
