package io.really.quicksand

import com.typesafe.config._
import akka.actor._
import akka.testkit._
import org.scalatest._
import io.really.quickSand.QuickSand

import io.really.ReallyConfig

class QuickSandSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("QuickSandSpec"))

  val conf = ConfigFactory.parseString("""
                really.quicksand.workerId=1, really.quicksand.datacenterId = 1,
                really.quicksand.epoch = 1410765153,
                really.persistence.object-persistor-timeout = 1s,
                akka={}
                                       """)
  val config = new ReallyConfig(conf)

  lazy val quickSand = new QuickSand(config, system)

  "QuickSand" when {
    "generate Ids" must {
      "be unique" in {
        val result = (1 to 10000).map(_ => quickSand.nextId)
        result.distinct.size shouldBe result.size
      }
      "sortable" in {
         val result = (1 to 100000).map(_ => quickSand.nextId.toString)
         val sortedResult = result.zip(result.sorted)
         sortedResult.filter(r => r._1 != r._2).size shouldBe 0
      }
    }
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

}