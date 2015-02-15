/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.fixture

import io.really.fixture.CollectionActorTest.GetState
import io.really.model.CollectionActor.{ ObjectNotFound, State }
import io.really.{ InternalRequest, R, ReallyGlobals }
import io.really.model.CollectionActor

class CollectionActorTest(globals: ReallyGlobals) extends CollectionActor(globals) {

  override def handleInternalRequest: StateFunction = super.handleInternalRequest orElse handleTestingMessage

  def handleTestingMessage: StateFunction = {
    case Event(GetState(r), _) =>
      log.debug(s"$persistenceId Persistor received a GetState message for: $r")
      bucket.get(r).map { obj =>
        sender() ! State(obj.data)
      }.getOrElse {
        sender() ! ObjectNotFound(r)
      }
      stay
  }
}

class CollectionActorWithCleanJournal(globals: ReallyGlobals) extends CollectionActorTest(globals) {

  /** for clean journal after finish Test suite */
  override def postStop() = {
    deleteMessages(lastSequenceNr)
    super.postStop()
  }
}

object CollectionActorTest {
  case class GetState(r: R) extends InternalRequest
}
