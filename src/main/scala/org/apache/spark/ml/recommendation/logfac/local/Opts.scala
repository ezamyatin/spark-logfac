package org.apache.spark.ml.recommendation.logfac.local

object Opts {

  def implicitOpts(dim: Int,
                   useBias: Boolean,
                   negative: Int,
                   pow: Float,
                   lr: Float,
                   lambdaL: Float,
                   lambdaR: Float,
                   gamma: Float,
                   verbose: Boolean,
                   frozenL: Boolean,
                   frozenR: Boolean): Opts = {
    new Opts(dim, useBias, negative, pow, lr,
      lambdaL, lambdaR, gamma, true, verbose, frozenL, frozenR)
  }

  def explicitOpts(dim: Int,
                   useBias: Boolean,
                   lr: Float,
                   lambdaL: Float,
                   lambdaR: Float,
                   verbose: Boolean,
                   frozenL: Boolean,
                   frozenR: Boolean): Opts = {
    new Opts(dim, useBias, 0, Float.NaN, lr,
      lambdaL, lambdaR, Float.NaN, false, verbose, frozenL, frozenR)
  }
}

private[ml] class Opts private(val dim: Int,
                               val useBias: Boolean,
                               val negative: Int,
                               val pow: Float,
                               val lr: Float,
                               val lambdaL: Float,
                               val lambdaR: Float,
                               val gamma: Float,
                               val implicitPref: Boolean,
                               val verbose: Boolean,
                               val frozenL: Boolean,
                               val frozenR: Boolean) extends Serializable {

  if (!implicitPref && (negative != 0 || !gamma.isNaN || !pow.isNaN) ||
        frozenL && frozenR) {
    throw new IllegalArgumentException()
  }

  def vectorSize: Int = if (useBias) dim + 1 else dim
}
