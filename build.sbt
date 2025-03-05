name := "spark-logfac"
version := "0.1.1"
description := "Logistic Matrix Factorization over Spark"
organization := "com.github.ezamyatin"
scalaVersion := "2.12.19"
publishMavenStyle := true
parallelExecution := false
fork := true
javaOptions += "-Dlog4j2.configurationFile=file:./src/test/resources/log4j2.properties"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % "3.3.3",
  "org.apache.spark" %% "spark-core" % "3.3.3",
  "org.apache.spark" %% "spark-mllib" % "3.3.3",
  "org.apache.spark" %% "spark-core" % "3.3.3" classifier "tests",
  "org.apache.spark" %% "spark-sql" % "3.3.3" classifier "tests",
  "org.apache.spark" %% "spark-mllib" % "3.3.3" classifier "tests",
  "org.apache.spark" %% "spark-catalyst" % "3.3.3" classifier "tests",
  "org.scalatest" %% "scalatest" % "3.3.0-SNAP3" % Test
)
