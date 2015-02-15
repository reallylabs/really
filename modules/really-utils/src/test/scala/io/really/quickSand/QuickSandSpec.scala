/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.quickSand

import org.scalatest.{ Matchers, FlatSpec }

class QuickSandSpec extends FlatSpec with Matchers {
  lazy val quickSand = new QuickSand(1l, 1l, 1410765153)

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