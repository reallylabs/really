//package io.really.util
//
//import akka.actor._
//import io.really.withRequestContext
//
///**
// *  WORK IN PROGRESS
// */
//trait ReallyLoggingActor extends Actor {
//  import akka.event.Logging._
//  val log = akka.event.Logging(this)
//  def mdc(currentMessage: Any): MDC = currentMessage match {
//    case e: withRequestContext =>
//      Map("traceId" -> e.ctx.meta.traceId)
//    case _ => emptyMDC
//  }
//
//  override protected[akka] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = try {
//    log.mdc(mdc(msg))
//    super.aroundReceive(receive, msg)
//  } finally {
//    log.clearMDC()
//  }
//}
