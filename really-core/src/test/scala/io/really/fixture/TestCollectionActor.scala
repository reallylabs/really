/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.fixture

import io.really.ReallyGlobals
import io.really.model.CollectionActor

class TestCollectionActor(globals: ReallyGlobals) extends CollectionActor(globals) {

  /** for clean journal after finish Test suite */
  override def postStop() = {
    //    println("....................................... " + lastSequenceNr)
    deleteMessages(lastSequenceNr)
    super.postStop()
  }

}
