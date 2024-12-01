package org.apache.spark.ml.recommendation.logfac.local

private[ml] object ItemData {
  val TYPE_LEFT = false
  val TYPE_RIGHT = true
}

private[ml] case class ItemData(t: Boolean,
                                id: Long,
                                cn: Long,
                                f: Array[Float]) extends Serializable
