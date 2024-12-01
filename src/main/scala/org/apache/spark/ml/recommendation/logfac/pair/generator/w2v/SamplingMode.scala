package org.apache.spark.ml.recommendation.logfac.pair.generator.w2v

private[ml] object SamplingMode extends Enumeration {
  type SamplingMode = Value
  val WINDOW, ITEM2VEC = Value
}
