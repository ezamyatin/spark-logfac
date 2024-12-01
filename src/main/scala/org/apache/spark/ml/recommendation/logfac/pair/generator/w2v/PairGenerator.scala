package org.apache.spark.ml.recommendation.logfac.pair.generator.w2v

import org.apache.spark.Partitioner
import org.apache.spark.ml.recommendation.logfac.pair.LongPair

private[ml] abstract class PairGenerator(private val sent: Iterator[Array[Long]],
                                         protected val partitioner1: Partitioner,
                                         protected val partitioner2: Partitioner
                                        ) extends Iterator[LongPair] with Serializable {
  assert(partitioner1.numPartitions == partitioner2.numPartitions)
  private var it: Iterator[LongPair] = Iterator.empty

  protected def generate(sent: Array[Long]): Iterator[LongPair]

  override def hasNext: Boolean = {
    while (!it.hasNext && sent.hasNext) {
      it = generate(sent.next())
    }

    it.hasNext
  }

  override def next(): LongPair = {
    while (!it.hasNext && sent.hasNext) {
      it = generate(sent.next())
    }

    if (it.hasNext) {
      return it.next()
    }

    throw new NoSuchElementException("next on empty iterator")
  }
}
