package org.apache.spark.ml.recommendation.logfac

import java.io.IOException
import java.util.Random

import scala.collection.mutable.ArrayBuffer

import org.apache.hadoop.fs.Path

import org.apache.spark.{HashPartitioner, Partitioner}
import org.apache.spark.internal.Logging
import org.apache.spark.ml.recommendation.logfac.local.{ItemData, Optimizer, Opts}
import org.apache.spark.ml.recommendation.logfac.pair.LongPairMulti
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

private[ml] object LogFacBase {
  private val PART_TABLE_TOTAL_SIZE = 10000000

  private def shuffle(arr: Array[Int], rnd: Random) = {
    var i = 0
    val n = arr.length
    var t = 0
    while (i < n - 1) {
      val j = i + rnd.nextInt(n - i)
      t = arr(j)
      arr(j) = arr(i)
      arr(i) = t
      i += 1
    }
    arr
  }

  def createPartitionTable(numPartitions: Int, rnd: Random): Array[Array[Int]] = {
    val nBuckets = PART_TABLE_TOTAL_SIZE / numPartitions
    val result = Array.fill(numPartitions)(Array.fill(nBuckets)(0))
    (0 until nBuckets).foreach{i =>
      val arr = (0 until numPartitions).toArray
      shuffle(arr, rnd)
      (0 until numPartitions).foreach(j => result(j)(i) = arr(j))
    }
    result
  }

  def hash(i: Long, salt: Int, n: Int): Int = {
    var h = (i.hashCode.toLong << 32) | salt
    h ^= h >>> 33
    h *= 0xff51afd7ed558ccdL
    h ^= h >>> 33
    h *= 0xc4ceb9fe1a85ec53L
    h ^= h >>> 33
    (Math.abs(h) % n).toInt
  }
}

private[ml] abstract class LogFacBase[T](
             dotVectorSize: Int = 10,
             negative: Int = 10,
             numIterations: Int = 1,
             learningRate: Double = 0.025,
             numThread: Int = 1,
             numPartitions: Int = 1,
             pow: Double = 0,
             lambdaU: Double = 0,
             lambdaI: Double = 0,
             useBias: Boolean = false,
             implicitPrefs: Boolean = true,
             seed: Long = 0,
             intermediateRDDStorageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK,
             finalRDDStorageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK,
             checkpointInterval: Int = -1)
  extends Serializable with Logging {

  protected def gamma: Double = 1.0

  protected def cacheAndCount[E](rdd: RDD[E]): RDD[E] = {
    val r = rdd.persist(intermediateRDDStorageLevel)
    r.count()
    r
  }

  protected def pairs(data: RDD[T],
                      partitioner1: Partitioner,
                      partitioner2: Partitioner,
                      seed: Long): RDD[LongPairMulti]

  protected def initialize(data: RDD[T]): RDD[ItemData]

  private def deletePreviousCheckpointFile(previousCheckpointFile: Option[String])(
    implicit sqlc: SQLContext): Unit =
    previousCheckpointFile.foreach { file =>
      try {
        val checkpointFile = new Path(file)
        checkpointFile.getFileSystem(sqlc.sparkContext.hadoopConfiguration)
          .delete(checkpointFile, true)
      } catch {
        case e: IOException =>
          logWarning(f"Cannot delete checkpoint file $file:", e)
      }
    }

  private def checkpoint(rdd: RDD[ItemData])(implicit sqlc: SQLContext): (RDD[ItemData], String) = {
    import sqlc.implicits._
    val path = sqlc.sparkContext.getCheckpointDir.get + "/" + scala.util.Random.nextLong()
    rdd.toDF().write.parquet(path)
    sqlc.read.parquet(path).as[ItemData].rdd -> path
  }

  private def shouldCheckpoint(iter: Int)(
    implicit sqlc: SQLContext): Boolean =
    sqlc.sparkContext.checkpointDir.isDefined &&
      checkpointInterval != -1 && (iter % checkpointInterval == 0)

  private[recommendation] def train(data: RDD[T],
                                    frozenL: Option[RDD[(Long, Array[Float], Float)]],
                                    frozenR: Option[RDD[(Long, Array[Float], Float)]]
                                   )(implicit sqlc: SQLContext): RDD[ItemData] = {
    val cached = ArrayBuffer.empty[RDD[ItemData]]
    var emb = initialize(data)

    emb = frozenL.fold(emb)(frozen => emb.keyBy(i => i.id -> i.t)
      .leftOuterJoin(frozen.map(i => (i._1 -> ItemData.TYPE_LEFT) -> (i._2, i._3))).values
      .map{v => v._2.foreach{x =>
        System.arraycopy(x._1, 0, v._1.f, 0, dotVectorSize)
        if (useBias) {
          v._1.f(dotVectorSize) = x._2
        }
      }; v._1})

    emb = frozenR.fold(emb)(frozen => emb.keyBy(i => i.id -> i.t)
      .leftOuterJoin(frozen.map(i => (i._1 -> ItemData.TYPE_RIGHT) -> (i._2, i._3))).values
      .map{v => v._2.foreach{x =>
        System.arraycopy(x._1, 0, v._1.f, 0, dotVectorSize)
        if (useBias) {
          v._1.f(dotVectorSize) = x._2
        }
      }; v._1})

    emb = cacheAndCount(emb)
    cached += emb

    var checkpointIter = 0
    val partitionTable = LogFacBase
      .createPartitionTable(numPartitions, new Random(seed))

    var previousCheckpointFile: Option[String] = None

    (0 until numIterations).foreach {curEpoch =>

      val partitioner1 = new HashPartitioner(numPartitions) {
        override def getPartition(item: Any): Int = {
          LogFacBase.hash(item.asInstanceOf[Long], curEpoch, this.numPartitions)
        }
      }

      (0 until numPartitions).foreach { pI =>
        val bucket2part = partitionTable(pI)
        val partitioner2 = new HashPartitioner(numPartitions) {
          override def getPartition(item: Any): Int = {
            val bucket = LogFacBase.hash(
              item.asInstanceOf[Long],
              curEpoch,
              bucket2part.length)
            bucket2part(bucket)
          }
        }

        val partitionerKey = new HashPartitioner(numPartitions) {
          override def getPartition(key: Any): Int = key.asInstanceOf[Int]
        }

        val embLR = emb
          .keyBy(i => if (i.t == ItemData.TYPE_LEFT) {
            partitioner1.getPartition(i.id)
          } else {
            partitioner2.getPartition(i.id)
          }).partitionBy(partitionerKey).values

        val cur = pairs(data, partitioner1, partitioner2,
            (1L * curEpoch * numPartitions + pI) * numPartitions)
          .map(e => e.part -> e).partitionBy(partitionerKey).values

        emb = cur.zipPartitions(embLR) { case (sIt, eItLR) =>
          val opts = if (implicitPrefs) {
            Opts.implicitOpts(dotVectorSize, useBias, negative, pow.toFloat,
              learningRate.toFloat, lambdaU.toFloat, lambdaI.toFloat,
              gamma.toFloat, verbose = false,
              frozenL.isDefined, frozenR.isDefined)
          } else {
            Opts.explicitOpts(dotVectorSize, useBias, learningRate.toFloat,
              lambdaU.toFloat, lambdaI.toFloat, verbose = false,
              frozenL.isDefined, frozenR.isDefined)
          }
          val sg = Optimizer(opts, eItLR)

          sg.optimize(sIt, numThread, remapInplace = true)

          sg.flush()
        }.persist(intermediateRDDStorageLevel)

        cached += emb

        if (shouldCheckpoint(checkpointIter + 1)) {
          val (newEmb, newCheckpointFile) = checkpoint(emb)
          emb = newEmb
          deletePreviousCheckpointFile(previousCheckpointFile)
          previousCheckpointFile = Some(newCheckpointFile)
          cached.foreach(_.unpersist())
          cached.clear()
        }
        checkpointIter += 1
      }
    }

    emb = emb.map(identity).persist(finalRDDStorageLevel)
    emb.count()

    cached.foreach(_.unpersist())
    cached.clear()

    emb
  }
}
