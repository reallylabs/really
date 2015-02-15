/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import io.really.{ R, ReallyConfig, TestConf }
import org.scalatest.{ Matchers, FlatSpec }

class HelpersSpec extends FlatSpec with Matchers {

  implicit val config: ReallyConfig = TestConf.getConfig()
  "Get BucketID from R" should "fail when r doesn't contain ID" in {

    val r1 = R / "users"
    intercept[IllegalArgumentException] {
      Helpers.getBucketIDFromR(r1)
    }
    val r2 = R / "users" / 123 / "books"
    intercept[IllegalArgumentException] {
      Helpers.getBucketIDFromR(r2)
    }
  }
  it should "pass when r is object" in {
    val r = R / "users" / 123 / "books" / 456
    val bucketID = Helpers.getBucketIDFromR(r)
    val bucketIDVal = bucketID.split('-')
    bucketID.length should be > 0
    bucketIDVal.length should be > 0
    bucketIDVal(1).toInt should be > 0
  }

  "Get R from BuckerID" should "return skeleton of original r object" in {
    val r = R / "users" / 123 / "books" / 456
    val bucketId = Helpers.getBucketIDFromR(r)
    val bucketIdR = Helpers.getRFromBucketID(bucketId)
    bucketIdR should equal(r.skeleton)
  }

  it should "return InvalidArgumentException if bucketID not valid string" in {
    val bucketId = "Invalid BucketID"
    intercept[IllegalArgumentException] {
      Helpers.getRFromBucketID(bucketId)
    }
  }
}