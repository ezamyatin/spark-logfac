# spark-logfac
## Logistic Matrix Factorizations over Spark
The package contains highly scalable matrix factorization algorithms with a logistic loss function:
- LMF [1]
- Item2Vec [2]

## Setup
```scala
resolvers += "Spark Packages Repo" at "https://repos.spark-packages.org/"

libraryDependencies += "ezamyatin" % "spark-logfac" % "0.1.0"
```

## Overview
Both algorithms implement the general gradient descent method for matrix factorizations. The key principle of the algorithm is as follows: each training epoch consists of n subiterations, where n is the number of partitions into which the embeddings and data are partitioned. The value of n should be chosen so that 1/n part of the embeddings fits into the memory of a single executor. Data and embeddings are partitioned using two hash functions, one for “rows” and another for “columns”. Thus one table of embeddings (user-side) is partitioned using the first hash function and the second (item-side) using the second hash function. The data is a set of user-item pairs. 
On each subiteration, only those pairs whose embeddings of both elements are present on the excecutor are processed. To process all pairs, each of the n subiterations corresponds to a k-th cyclic shift of item-side partitioning.

Within the subiteration, local SGD is performed. The key difficulty here is negative sampling for implicit approaches. To solve this problem we used the method described in the paper "Distributed negative sampling for word embeddings" [3], the essence of which is that to use embeddings available at the executor.

## References
[1] [Logistic Matrix Factorization](https://web.stanford.edu/~rezab/nips2014workshop/submits/logmat.pdf)

[2] [Item2Vec](https://ceur-ws.org/Vol-1688/paper-13.pdf)

[3] [Distributed negative sampling for word embeddings](https://ojs.aaai.org/index.php/AAAI/article/view/10931/10790)
