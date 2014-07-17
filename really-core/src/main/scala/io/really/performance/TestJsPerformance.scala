//package io.really.performance
//
//import akka.actor._
//import akka.pattern.ask
//import akka.util.Timeout
//import io.really._
//import io.really.model.{CollectionMetadata, JsHooks, Model, StringField}
//import play.api.libs.json.{JsString, Json}
//
//import scala.concurrent.Future
//import scala.concurrent.duration._
//
//case object Done
//case object StartTest
//
//class JsExecutor(model: Model) extends Actor with ActorLogging {
//  val obj = Json.obj("key" -> "value")
//  def receive = {
//    case StartTest =>
//      val max = 5000
//      log.warning(s"Starting JS Test on $max iterations")
//      val t1 = System.currentTimeMillis()
//      for (i <- 1 to max) {
//        model.executeValidate(null, obj)
//      }
//      val t2 = System.currentTimeMillis()
//      log.warning(s"agent finished in ${t2-t1}ms")
//      sender() ! Done
//
//  }
//}
//
//class TestJsPerformance(globals: ReallyGlobals) extends Actor with ActorLogging {
//  implicit val ec = context.dispatcher
//  implicit val to = Timeout(35.seconds)
//  val obj = Json.obj("key" -> "value")
//  val model = Model(R / 'users, CollectionMetadata(1), Map("name" -> StringField("name", JsString("aykalam"))),
//    JsHooks(Some("""
//               | JSON.stringify(input);
//               | """.stripMargin),
//      None,
//      None,
//      None,
//      None,
//      None,
//      None), null)
//
//  val r1 = context.actorOf(Props(new JsExecutor(model)))
//  val r2 = context.actorOf(Props(new JsExecutor(model)))
//  val r3 = context.actorOf(Props(new JsExecutor(model)))
//  val r4 = context.actorOf(Props(new JsExecutor(model)))
//  val r5 = context.actorOf(Props(new JsExecutor(model)))
//  val r6 = context.actorOf(Props(new JsExecutor(model)))
//  val r7 = context.actorOf(Props(new JsExecutor(model)))
//  val r8 = context.actorOf(Props(new JsExecutor(model)))
//
//  def receive = {
//    case StartTest =>
//      val t1 = System.currentTimeMillis()
//      Future.sequence(List(r1 ? StartTest,
//      r2 ? StartTest,
//      r3 ? StartTest,
//      r4 ? StartTest,
//      r5 ? StartTest,
//      r6 ? StartTest,
//      r7 ? StartTest,
//      r8 ? StartTest)).onSuccess {
//        case e =>
//          val t2 = System.currentTimeMillis()
//          log.error(s"Test (8000) finished in ${t2-t1}")
//      }
//
//
//  }
//}