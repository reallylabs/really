package io.really.quicksand

import io.really.quickSand.QuickSand
import io.really.BaseActorSpec

class QuickSandSpec extends BaseActorSpec {
  lazy val quickSand = new QuickSand(config, system)

  "QuickSand" should "generate Ids be unique" in {
    val result = (1 to 10000).map(_ => quickSand.nextId)
    result.distinct.size shouldBe result.size
  }

  it should "generate sortable Ids " in {
    val result = (1 to 100000).map(_ => quickSand.nextId.toString)
    val sortedResult = result.zip(result.sorted)
    sortedResult.filter(r => r._1 != r._2).size shouldBe 0
  }

}