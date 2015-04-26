package io.really

import scala.util.Random

object TestHelpers {
  def randomBucketID(r: R)(implicit config: ReallyConfig): BucketID = {
    require(r.isObject, "R doesn't contain an ID")
    r.skeleton.actorFriendlyStr + "-" + (r.head.id.get % config.Sharding.bucketsNumber) + "-" + Random.nextString(4)
  }

}