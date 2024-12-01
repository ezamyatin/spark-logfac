package org.apache.spark.ml.recommendation.logfac.pair

import javax.annotation.Nullable

private[ml] case class LongPairMulti(part: Int,
                                     left: Array[Long],
                                     right: Array[Long],
                                     @Nullable label: Array[Float] = null,
                                     @Nullable weight: Array[Float] = null
                                    ) extends Serializable
