package io.really.model

import akka.actor._
import akka.testkit._
import org.scalatest._

class CollectionActorSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll{
  def this() = this(ActorSystem("PersistenceSystem"))

  "CollectionActor" should "pass if the JS validation script ended without calling cancel()" in {

  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }
}