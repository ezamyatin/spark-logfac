package org.apache.spark.ml.recommendation.logfac.pair.generator.w2v

import org.apache.spark.Partitioner
import org.apache.spark.ml.recommendation.logfac.pair.generator.PrimitiveArrayBuffer
import org.apache.spark.ml.recommendation.logfac.pair.LongPair


private[ml] class WindowGenerator(sent: Iterator[Array[Long]],
                                  private val window: Int,
                                  partitioner1: Partitioner,
                                  partitioner2: Partitioner
                                 ) extends PairGenerator(sent, partitioner1, partitioner2) {
  final private val p1 = PrimitiveArrayBuffer.empty[Int]
  final private val p2 = PrimitiveArrayBuffer.empty[Int]

  override protected def generate(sent: Array[Long]): Iterator[LongPair] = {
    p1.clear()
    p2.clear()

    sent.indices.foreach{i =>
      p1.add(partitioner1.getPartition(sent(i)))
      p2.add(partitioner2.getPartition(sent(i)))
    }

    new Iterator[LongPair] {
      private var i = 0
      private var j = -window

      override def hasNext: Boolean = true

      override def next(): LongPair = {
        while (i < sent.length) {
          j = Math.max(j, -i)
          while (j <= window && i + j < sent.length) {
            val c = i + j
            j += 1

            if ((p1.get(i) == p2.get(c)) && sent(i) != sent(c)) {
              return LongPair(p1.get(i), sent(i), sent(c))
            }
          }
          i += 1
          j = -window
        }

        null
      }
    }.takeWhile(_ != null)
  }
}
