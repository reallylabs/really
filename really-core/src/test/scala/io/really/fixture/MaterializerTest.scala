package io.really.fixture

import io.really.model.materializer.CollectionViewMaterializer
import io.really.{ BucketID, ReallyGlobals }

class MaterializerTest(globals: ReallyGlobals) extends CollectionViewMaterializer(globals) {
  import io.really.fixture.MaterializerTest._

  def testOps: Receive = {
    case GetState(_) =>
      sender ! materializerCurrentState
  }

  override def generalOps: Receive = super.generalOps orElse testOps

}

object MaterializerTest {
  case class GetState(bucketId: BucketID) extends CollectionViewMaterializer.RoutableToMaterializer
}
