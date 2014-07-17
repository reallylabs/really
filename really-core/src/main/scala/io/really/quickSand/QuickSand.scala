/** Inspired by Snowflake Twitter */

package io.really.quickSand

import akka.actor.ActorSystem
//import akka.event.LogSource

import org.joda.time.DateTime
import io.really.ReallyConfig

class QuickSand(val config: ReallyConfig, actorSystem: ActorSystem) {

  // Logger is Akka's event logger
  //implicit val logSource: LogSource[QuickSand] = LogSource.fromClass[classOf[QuickSand]]
  val log = akka.event.Logging(actorSystem, "QuickSand")

  private[this] val workerId: Long = config.QuickSand.workerId
  private[this] val datacenterId: Long = config.QuickSand.datacenterId
  log.debug("QuickSand Started, workerId={} and datacenterId={}", workerId, datacenterId)
  private[this] val reallyEpoch: Long = config.QuickSand.reallyEpoch

  private[this] val workerIdBits = 5L
  private[this] val datacenterIdBits = 5L
  private[this] val maxWorkerId = -1L ^ (-1L << workerIdBits)
  private[this] val maxDatacenterId = -1L ^ (-1L << datacenterIdBits)
  private[this] val sequenceBits = 12L

  private[this] val workerIdShift = sequenceBits
  private[this] val datacenterIdShift = sequenceBits + workerIdBits
  private[this] val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits
  private[this] val sequenceMask = -1L ^ (-1L << sequenceBits)

  private[this] var sequence = 0l
  private[this] var lastTimestamp = -1l

  if (workerId > maxWorkerId || workerId < 0) {
    throw new IllegalArgumentException("worker Id can't be greater than %d or less than 0".format(maxWorkerId))
  }

  if (datacenterId > maxDatacenterId || datacenterId < 0) {
    throw new IllegalArgumentException("datacenter Id can't be greater than %d or less than 0".format(maxDatacenterId))
  }

  /**
   * Might throw [[InvalidSystemClock]] Exception if the clock is skewed, beware.
   * @return
   */
  def nextId(): Long = {
    synchronized {
      var timestamp = DateTime.now.getMillis

      if (timestamp < lastTimestamp) {
        log.error("clock is moving backwards. Rejecting requests until %d.", lastTimestamp);
        throw new InvalidSystemClock("Clock moved backwards. Refusing to generate id for %d milliseconds".format(
          lastTimestamp - timestamp))
      }

      if (lastTimestamp == timestamp) {
        sequence = (sequence + 1) & sequenceMask
        if (sequence == 0) {
          timestamp = tilNextMillis(lastTimestamp)
        }
      } else {
        sequence = 0
      }

      lastTimestamp = timestamp

      ((timestamp - reallyEpoch) << timestampLeftShift) |
        (datacenterId << datacenterIdShift) |
        (workerId << workerIdShift) |
        sequence
    }
  }

  protected def tilNextMillis(lastTimestamp: Long): Long = {
    var timestamp = DateTime.now.getMillis
    while (timestamp <= lastTimestamp) {
      timestamp = DateTime.now.getMillis
    }
    timestamp
  }

}

class InvalidSystemClock(message: String) extends Exception(message)
