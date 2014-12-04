/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import com.github.simplyscala.{ MongodProps, MongoEmbedDatabase }
import org.scalatest.BeforeAndAfterAll

abstract class BaseActorSpecWithMongoDB extends BaseActorSpec with MongoEmbedDatabase with BeforeAndAfterAll {
  private var mongoProps: MongodProps = null

  override def beforeAll() = {
    mongoProps = mongoStart()
    super.beforeAll()
  }

  override def afterAll() = {
    mongoStop(mongoProps)
    super.afterAll()
  }

}