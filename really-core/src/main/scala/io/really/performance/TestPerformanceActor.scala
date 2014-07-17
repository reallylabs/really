//package io.really.performance
//
//import akka.actor._
//import akka.pattern.ask
//import akka.util.Timeout
//import io.really._
//import io.really.model.{CollectionActor, _}
//import play.api.libs.json._
//
//import scala.concurrent.Future
//import scala.concurrent.duration._
//
//class TestPerformanceActor(globals: ReallyGlobals) extends Actor with ActorLogging {
//
//  implicit val ec = context.dispatcher
//  implicit val to = Timeout(35.seconds)
//
//  val model = Model(R / 'users, CollectionMetadata(1), Map("name" -> StringField("name", JsString("aykalam"))),
//      JsHooks(Some("""input.key + 'ahmed' """),
//          None,
//          None,
//          None,
//          None,
//          None,
//          None), null)
//
//  def receive = {
//    case StartTest =>
//      val max = 100
//
//
//      val t1 = System.currentTimeMillis()
//      val results = for (i <- 1 to max) yield {
//        val r = R / 'users / i
//        val obj = context.actorOf(ObjectPersistor.props(globals, r, model), r.actorFriendlyStr)
//        (obj ? Create(null, Json.obj("key" -> "value"))).map {
//          case a: AlreadyExists =>
//            //println(s"Object $r already exists")
//            0
//          case b: ObjectCreated =>
//            1
//          case e =>
//            println(s"GOT SOMETHING WE DO NOT UNDERSTAND $e")
//            0
//        }
//      }
//      val f = Future.sequence(results)
//      f.onSuccess {
//        case s =>
//          val t2 = System.currentTimeMillis()
//          val total = s.reduceLeft(_ + _)
//          println(s"***********creating $total took ${t2-t1}ms*******************")
//      }
//      f.onFailure {
//        case f =>
//          println(s"failure: " + f)
//      }
//    case CollectionActor.ObjectActorIsIdle(actor, r) =>
//      context.stop(actor)
//  }
//}