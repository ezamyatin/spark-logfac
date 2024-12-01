package org.apache.spark.ml.recommendation.logfac.pair

private[ml] object LongPair {
  val EMPTY: Float = Float.NaN
}

private[ml] case class LongPair(part: Int,
                                left: Long,
                                right: Long,
                                label: Float = LongPair.EMPTY,
                                weight: Float = LongPair.EMPTY) extends Serializable
