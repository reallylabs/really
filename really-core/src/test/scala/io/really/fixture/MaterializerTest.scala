package io.really.fixture

import scala.util.Either
import scala.concurrent.Future
import akka.actor.ActorRef
import akka.pattern.pipe
import akka.persistence.{ SaveSnapshotSuccess, SaveSnapshotFailure }
import play.api.libs.json._
import io.really.model.{ ModelVersion, Model }
import io.really.model.materializer.CollectionViewMaterializer
import io.really.{ BucketID, ReallyGlobals, Revision, R }

class MaterializerTest(globals: ReallyGlobals, reportTo: ActorRef) extends CollectionViewMaterializer(globals) {
  import io.really.fixture.MaterializerTest._

  def testReceive: Receive = {
    case GetState(_) =>
      sender ! materializerCurrentState
  }

  def reportAny = new Receive {
    def isDefinedAt(msg: Any) = {
      reportTo ! msg
      false
    }
    def apply(msg: Any) = throw new MatchError(msg)
  }

  override def withoutModel: Receive = reportAny orElse super.withoutModel orElse testReceive

  override def withModel(model: Model, referencedCollections: List[R]): Receive =
    reportAny orElse super.withModel(model, referencedCollections) orElse testReceive

}

object MaterializerTest {
  case class GetState(bucketId: BucketID) extends CollectionViewMaterializer.RoutableToMaterializer
  case object SnapshotSaved
}
