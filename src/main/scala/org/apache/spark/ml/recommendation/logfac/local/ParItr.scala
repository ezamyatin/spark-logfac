package org.apache.spark.ml.recommendation.logfac.local

import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

private[ml] object ParItr {
  def foreach[A](iterator: Iterator[A], cpus: Int, fn: A => Unit): Unit = {
    val inQueue = new LinkedBlockingQueue[A](cpus * 5)
    val error = new AtomicReference[Exception](null)
    val end = new AtomicBoolean(false)
    val latch = new CountDownLatch(1)

    val threads = Array.fill(cpus) {
      new Thread(() => {
        try {
          while (!end.get() || inQueue.size() > 0) {
            fn(inQueue.take)
          }
        } catch {
          case _: InterruptedException =>
          case e: Exception => error.set(e)
        } finally {
          latch.countDown()
        }
      })
    }

    try {
      threads.foreach(_.start())
      iterator.foreach(e => {
        var ok = false
        while (!ok) {
          if (error.get() != null) {
            throw error.get()
          }

          ok = inQueue.offer(e, 1, TimeUnit.SECONDS)
        }
      })

      end.set(true)
      if (inQueue.size() > 0) {
        latch.await()
      }

      if (error.get() != null) {
        throw error.get()
      }

    } finally {
      threads.foreach(_.interrupt())
      threads.foreach(_.join())
    }
  }
}
