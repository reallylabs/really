/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

/** Inspired by Snowflake Twitter */

package io.really.quickSand

import org.joda.time.DateTime
import org.slf4j.LoggerFactory

class QuickSand(workerId: Long, datacenterId: Long, reallyEpoch: Long) {

  def logger = LoggerFactory.getLogger("Quicksand")

  logger.debug("QuickSand Started, workerId={} and datacenterId={}", workerId, datacenterId)

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
        logger.error("clock is moving backwards. Rejecting requests until %d.", lastTimestamp)
        throw new InvalidSystemClock("Clock moved backwards. Refusing to generate id for %d milliseconds".format(
          lastTimestamp - timestamp
        ))
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
