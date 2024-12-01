lazy val root = (project in file("."))
  .settings(
    name := "spark-logfac",
    organization := "com.github.ezamyatin",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.12.19"
  )

parallelExecution in ThisBuild := false
fork := true
javaOptions += "-Dlog4j2.configurationFile=file:/Users/e.zamyatin/vk/spark-logfac/src/test/resources/log4j2.properties"

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