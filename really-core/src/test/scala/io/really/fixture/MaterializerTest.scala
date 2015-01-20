package io.really.fixture

import scala.util.Either
import scala.concurrent.Future
import akka.actor.ActorRef
import akka.pattern.pipe
import akka.persistence.{ SnapshotSelectionCriteria }
import play.api.libs.json._
import io.really.model.{ ModelVersion, Model }
import io.really.model.materializer.CollectionViewMaterializer
import io.really.{ BucketID, ReallyGlobals, Revision, R }

class MaterializerTest(globals: ReallyGlobals, reportTo: ActorRef) extends CollectionViewMaterializer(globals) {
  import io.really.fixture.MaterializerTest._

  def testReceive: StateFunction = {
    case Event(GetState(_), _) =>
      sender ! materializerCurrentState
      stay
  }

  def reportAny = new StateFunction {
    def isDefinedAt(msg: Event) = {
      reportTo ! msg.event
      false
    }
    def apply(msg: Event) = throw new MatchError(msg)
  }

  override def handleModelCreated: StateFunction = reportAny orElse super.handleModelCreated orElse testReceive

  override def handleModelOperations: StateFunction = reportAny orElse super.handleModelOperations orElse testReceive

  /** for clean journal after finish Test suite */
  override def postStop() = {
    deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = lastSequenceNr))
    super.postStop()
  }

}

object MaterializerTest {
  case class GetState(bucketId: BucketID) extends CollectionViewMaterializer.RoutableToMaterializer
  case object SnapshotSaved
}
