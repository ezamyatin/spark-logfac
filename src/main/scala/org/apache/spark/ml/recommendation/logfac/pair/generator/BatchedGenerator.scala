package org.apache.spark.ml.recommendation.logfac.pair.generator

import org.apache.spark.ml.recommendation.logfac.pair.{LongPair, LongPairMulti}

private[ml] object BatchedGenerator {
  final private val TOTAL_BATCH_SIZE = 10000000

  def apply(pairGenerator: Iterator[LongPair],
            numPartitions: Int,
            withLabel: Boolean,
            withWeight: Boolean): BatchedGenerator = {
    val left = Array.fill(numPartitions)(PrimitiveArrayBuffer.empty[Long])
    val right = Array.fill(numPartitions)(PrimitiveArrayBuffer.empty[Long])
    val label = if (withLabel) {
      Array.fill(numPartitions)(PrimitiveArrayBuffer.empty[Float])
    } else {
      null.asInstanceOf[Array[PrimitiveArrayBuffer[Float]]]
    }
    val weight = if (withWeight) {
      Array.fill(numPartitions)(PrimitiveArrayBuffer.empty[Float])
    } else {
      null.asInstanceOf[Array[PrimitiveArrayBuffer[Float]]]
    }

    new BatchedGenerator(pairGenerator, left, right, label, weight,
      TOTAL_BATCH_SIZE / numPartitions)
  }
}

private[ml] class BatchedGenerator(private val pairGenerator: Iterator[LongPair],
                                   private val left: Array[PrimitiveArrayBuffer[Long]],
                                   private val right: Array[PrimitiveArrayBuffer[Long]],
                                   private val label: Array[PrimitiveArrayBuffer[Float]],
                                   private val weight: Array[PrimitiveArrayBuffer[Float]],
                                   private val batchSize: Int
                                  ) extends Iterator[LongPairMulti] with Serializable {

  private var nonEmptyCounter = 0
  private var ptr = 0

  override def hasNext: Boolean = pairGenerator.hasNext || nonEmptyCounter > 0

  override def next(): LongPairMulti = {
    while (pairGenerator.hasNext) {
      val pair = pairGenerator.next()
      val part = pair.part

      if (left(part).size == 0) {
        nonEmptyCounter += 1
      }

      left(part).add(pair.left)
      right(part).add(pair.right)
      if (label != null) label(part).add(pair.label)
      if (weight != null) weight(part).add(pair.weight)

      if (left(part).size >= batchSize) {
        val result = LongPairMulti(part,
          left(part).toArray, right(part).toArray,
          if (label == null) null else label(part).toArray,
          if (weight == null) null else weight(part).toArray
        )

        left(part).clear()
        right(part).clear()
        if (label != null) label(part).clear()
        if (weight != null) weight(part).clear()

        nonEmptyCounter -= 1
        return result
      }
    }

    while (ptr < left.length && left(ptr).size == 0) {
      ptr += 1
    }

    if (ptr < left.length) {
      val result = LongPairMulti(ptr,
        left(ptr).toArray, right(ptr).toArray,
        if (label == null) null else label(ptr).toArray,
        if (weight == null) null else weight(ptr).toArray
      )

      left(ptr).clear()
      right(ptr).clear()
      if (label != null) label(ptr).clear()
      if (weight != null) weight(ptr).clear()
      nonEmptyCounter -= 1
      return result
    }

    throw new NoSuchElementException("next on empty iterator")
  }
}
