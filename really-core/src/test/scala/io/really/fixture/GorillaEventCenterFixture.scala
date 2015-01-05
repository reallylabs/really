package io.really.fixture

import io.really.{ R, ReallyGlobals }
import io.really.gorilla.{ RoutableToGorillaCenter, GorillaEventCenter }
import scala.slick.driver.H2Driver.simple._

class GorillaEventCenterFixture(globals: ReallyGlobals)(implicit session: Session) extends GorillaEventCenter(globals) {
  import io.really.fixture.GorillaEventCenterFixture._

  def testOps: Receive = {
    case GetState(_) =>
      sender ! "done"
  }

  override def receive: Receive = super.receive orElse testOps

}

object GorillaEventCenterFixture {
  case class GetState(r: R) extends RoutableToGorillaCenter
}

