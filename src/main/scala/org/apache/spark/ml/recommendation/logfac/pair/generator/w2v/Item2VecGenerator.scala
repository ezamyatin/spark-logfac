package org.apache.spark.ml.recommendation.logfac.pair.generator.w2v

import java.util.Random

import org.apache.spark.Partitioner
import org.apache.spark.ml.recommendation.logfac.pair.generator.PrimitiveArrayBuffer
import org.apache.spark.ml.recommendation.logfac.pair.LongPair


private[ml] class Item2VecGenerator(sent: Iterator[Array[Long]],
                                    private val window: Int,
                                    partitioner1: Partitioner,
                                    partitioner2: Partitioner,
                                    seed: Long
                                   ) extends PairGenerator(sent, partitioner1, partitioner2) {
  final private val p1 = PrimitiveArrayBuffer.empty[Int]
  final private val p2 = PrimitiveArrayBuffer.empty[Int]
  final private val random = new Random(seed)

  override protected def generate(sent: Array[Long]): Iterator[LongPair] = {
    p1.clear()
    p2.clear()

    sent.indices.foreach{i =>
      p1.add(partitioner1.getPartition(sent(i)))
      p2.add(partitioner2.getPartition(sent(i)))
    }

    new Iterator[LongPair] {
      private var i = 0
      private var j = 0

      override def hasNext: Boolean = true

      override def next(): LongPair = {
        while (i < sent.length) {
          val n = Math.min(2 * window, sent.length - 1)
          while (j < n) {
            var c = i
            while (c == i) {
              c = random.nextInt(sent.length)
            }

            j += 1

            if ((p1.get(i) == p2.get(c)) && sent(i) != sent(c)) {
              return LongPair(p1.get(i), sent(i), sent(c))
            }
          }
          i += 1
          j = 0
        }
        null
      }
    }.takeWhile(_ != null)
  }
}
