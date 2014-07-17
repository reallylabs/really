//package io.really.performance
//
//import akka.actor._
//import com.typesafe.config.ConfigFactory
//import io.really._
//import play.api.libs.json._
//import io.really.boot.DefaultReallyGlobals
//import io.really.model.{JsHooks, StringField, CollectionMetadata, Model}
//import play.api.libs.json.JsString
//
//object PerfTestRunner {
//  def main(args: Array[String]): Unit = {
//    val config = new ReallyConfig(ConfigFactory.load())
//    val globals: ReallyGlobals = new DefaultReallyGlobals(config)
//    globals.boot()
//
//        val perfTest = globals.actorSystem.actorOf(Props(new TestPerformanceActor(globals)), "perf")
//        perfTest ! StartTest
//
//
//    readLine("enter any key to exit")
//    globals.actorSystem.shutdown()
//    globals.actorSystem.awaitTermination()
//
//  }
//}