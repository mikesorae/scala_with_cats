# Scala with Cats 9章 Case Study: Map-Reduce

* このケーススタディでは、Monoid、Fanctorやその他の便利なものを使ってシンプルだがパワフルな並列処理フレームワークを実装する
* Hadoopを使ったことがある人や、ビッグデータの仕事をしたことがある人なら MapReduce という言葉を聞いたことがあるだろう
* MapReduceは複数の計算機のクラスタをまたがって並列データ処理を行うためのプログラミングモデルである
* 名前が示すとおり、MapフェイズとReduceのフェイズからなる
* MapはScalaやFunctor型クラスのmap関数と同じ、ReduceはScalaで言うところのfoldと同じ (HadoopにはShuffleのフェイズもあるけどここでは無視する)

## 9.1 Parallelizing map and fold

一般化した`map` のシグネチャは `function A => B` を `F[A]` に適用して `F[B]` を返すことを思い出そう。

* mapは連続した要素の中のそれぞれを独立して変換する
* mapのそれぞれの要素に対する変換は互いに依存していないため、簡単に並列化することができる

```scala
def map[A, B](fa: F[A])(f: A => B): F[B]

def foldLeft[A, B](fa: F[A])(initial: B)(f: (B, A) => B): B
```

* foldとは何か
* Foldableのインスタンスを使ってこのステップを実装することができる
* すべてのfunctorがfoldableインスタンスを持っているわけではないが、これら両方の型クラスを持つデータ型の上にmap-reduceシステムを実装することができる
* reduceステップは分散mapの結果に対してfoldLeftになる

* reduceステップを分散化することにより、順序のコントロールができなくなる
* 全体としてleft-to-rightになることは保証できない
* 部分的なsequenceをleft-to-rightに処理し、結果を結合する
* 結果の正しさを保証するためにはreduceの操作が結合律を満たさなければならない

```
reduce(a1, reduce(a2, a3)) == reduce(reduce(a1, a2), a3)
```

* もし結合性を持つならば、部分的なsequenceが元のデータセットと同じ順序である限り任意のノード間で分散して作業することができる

* 我々のfold操作はBの型で計算を初期化する必要がある
* foldは任意の数の並列ステップに分割できるので、seedの処理は計算結果には影響してはいけない
* 当然ながらseedは単位元であることが要求される

```
reduce(seed, a1) == reduce(a1, seed) == a1
```

* つまり、我々の並列foldは以下が成立する限り正しい結果を出力する
  * reducer関数は結合律を満たすことを要求する
  * この関数の単位元をつかって計算をseedする

* 一周回ってMonoidに戻ってきた
* [monoid design pattern for map-reduce jobs](http://arxiv.org/abs/1304.7544) はTwitterのSummingbirdのような近年のビッグデータシステムのコアになっている
* このプロジェクトではとてもシンプルなsingle-machine map-reduceを実装してみる
* 我々の必要とするdata-flowをモデル化するための `foldMap` メソッドを実装するところから始める


## 9.2 Implementing foldMap

* Foldableを取り上げた際にfoldMapを見た
* これはfoldLeftとfoldRightの上にある派生操作の一つである
* ここではFoldableを使わず、map-reduceの構造の有益な洞察を得るため自分たちでfoldMapを再実装する

foldMapのシグネチャを書いてみる。以下のパラメータを受け取る

* Vector[A]型のシーケンス
* Bがモノイドである A => B の関数

```scala
import cats.Monoid

// foldMapのシグネチャ
def foldMap[A, B: Monoid](values: Vector[A])(func: A => B): B =
  ???
```

foldMapの実行フロー
1. data sequenceの初期化 ( seq = [○, ○, ○] )
  型Aのアイテムの配列を用意する
2. Map step ( apply func: ○ => ☆, seq = [☆, ☆, ☆] )
  型Bのアイテムの配列を求めるためリストを写像する
3. Final result ( result: ☆ )
  モノイドを使ってアイテムの配列を一つの型Bの要素にreduceする

出力例
```scala
import cats.instances.int._ // for Monoid

foldMap(Vector(1, 2, 3))(identity)
// res2: Int = 6

import cats.instances.string._ // for Monoid

// Mapping to a String uses the concatenation monoid:
foldMap(Vector(1, 2, 3))(_.toString + "! ")
// res4: String = "1! 2! 3! "

// Mapping over a String to produce a String:
foldMap("Hello world!".toVector)(_.toString.toUpperCase) 
// res6: String = HELLO WORLD!
```

実装は以下のようになる。
```scala
import cats.Monoid
import cats.instances.int._ // for Monoid
import cats.instances.string._ // for Monoid
import cats.syntax.semigroup._ // for |+|

// foldMapのシグネチャ
def foldMap[A, B: Monoid](values: Vector[A])(func: A => B): B =
    values.map(func).foldLeft(Monoid[B].empty)(_ |+| _)
```

次のように短く書くこともできる。

```scala
def foldMap[A, B : Monoid](as: Vector[A])(func: A => B): B =
  as.foldLeft(Monoid[B].empty)(_ |+| func(_))
```

## 9.3 Parallelising foldMap

* これでシングルスレッドで動くfoldMapの実装ができた
* シングルスレッド版のfoldMapを使ってブロックを組み立てる
* マルチコア版の実装はFigure 9.4で見ることができる

1. 処理したいすべてのデータの初期リストから始める
2. データを複数のbatchに分割し、一つのバッチをそれぞれのCPUに送る
3. 各CPUはbatch-levelで並列にMapフェイズを実行する
4. 各CPUはbatch-levelで並列にReduceフェイズを実行し、それぞれのbatchについてローカルの計算結果を生成する
5. 各batchの結果をReduceして最後の単一の結果にする

* Scalaはスレッド間での分散処理のためにいくつかのシンプルなツールを用意している
* このソリューションを実装するために[parallel collection library](http://docs.scala-lang.org/overviews/parallel-collections/overview.html)を使うことができるが、より深い理解のために自身でチャレンジし、Futureを使ったアルゴリズムを実装してみてほしい

## 9.3.1 Futures, Thread Pools, and ExecutionContexts

* 既に我々はFutureのモナディックな性質について多くを知っている
* 要点をおさらいしつつ、ScalaのFutureが裏側でどのように動いているのかを説明する

* Futureはimplicitで渡されたExecutionContextによって決められたThreadプール上で実行される
* Future.applyやその他のコンビネータでFutureを作成する際は、必ずスコープ内にimplicitなExecutionContextを用意しなければならない

```scala
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val future1 = Future {
  (1 to 100).toList.foldLeft(0)(_ + _)
}
// future1: scala.concurrent.Future[Int] = Future(<not completed>)

val future2 = Future {
  (100 to 200).toList.foldLeft(0)(_ + _)
}
// future2: scala.concurrent.Future[Int] = Future(<not completed>)
```

* この例では `ExecutionContext.Implicits.global` をインポートしている
* このデフォルトのコンテキストはCPU1つにつき1スレッドのスレッドプールを割り当てる
* Futureを作成したとき、ExecutionContextはその実行をスケジュールする
* もしスレッドプールにフリーなスレッドがあれば、Futureは直ちに実行される
* 最新型のコンピュータなら少なくとも2つのCPUをもっているので、この例のfuture1とfuture2は並列に実行されるだろう

* いくつかのコンビネータは、別のFutureの結果に基づいて計算を実行する新しいFutureを作成する
* 例えばmapやflatMapメソッドは、入力が計算されCPUが利用可能になったら直ちに実行される計算をスケジュールする

```scala
val future3 = future1.map(_.toString)
// future3: scala.concurrent.Future[String] = Future(<not completed>)

val future4 = for {
  a <- future1
  b <- future2
} yield a + b
// future4: scala.concurrent.Future[Int] = Future(<not completed>)
```

セクション7.2でみたように、Future.squenceを使うと `List[Future[A]]` を `Future[List[A]]` に変換することができる

```scala
 Future.sequence(List(Future(1), Future(2), Future(3)))
// res8: scala.concurrent.Future[List[Int]] = Future(<not completed>)
```

またはTraverseのインスタンスを使って

```scala
import cats.instances.future._ // for Applicative
import cats.instances.list._ // for Traverse
import cats.syntax.traverse._ // for sequence

List(Future(1), Future(2), Future(3)).sequence
// res9: scala.concurrent.Future[List[Int]] = Future(<not completed>)
```

* いずれのケースでもExecutionContextが必要となる。
* 最後に、`Await.result` を使うとFutureの結果が利用可能になるまでブロックすることができる

```scala
import scala.concurrent._
import scala.concurrent.duration._

Await.result(Future(1), 1.second) // wait for the result 
// res10: Int = 1
```

`cats.instances.future` には Futureで利用できるMonadとMonoidの実装もある。

```scala
import cats.{Monad, Monoid}
import cats.instances.int._    // for Monoid
import cats.instances.future._ // for Monad and Monoid

Monad[Future].pure(42)

Monoid[Future[Int]].combine(Future(1), Future(2))
// -> scala.concurrent.Future[Int] = Future(Success(3))
```

## 9.3.2 Dividing Work

* Futureに関する記憶をリフレッシュした
* どうやって作業を複数のbatchに分割するかを見てみよう
* Java標準ライブラリのAPIを使えば、我々のマシンでいくつのCPUが使えるかを問い合わせることができる

```scala
Runtime.getRuntime.availableProcessors
// res15: Int = 2
```

* groupedメソッドを使ってシーケンス(実際にはVectorを実装していれば何でも良い)を分割することができる
  * (?) Vectorはinterfaceじゃなさそうだけどどういうこと
* これを使ってCPUごとに作業のbatchを分割する

```scala
(1 to 10).toList.grouped(3).toList
// res16: List[List[Int]] = List(List(1, 2, 3), List(4, 5, 6), List(7, 8, 9), List(10))
```


## 9.3.3 Implementing parallelFoldMap

* `parallelFoldMap` という名前で並列実行版のfoldMapを実装する
* シグネチャは以下のとおり

```scala
def parallelFoldMap[A, B : Monoid]
      (values: Vector[A])
      (func: A => B): Future[B] = ???
```

* 前述のテクニックを使って作業を一つのCPUに対して一つのbatchになるよう分割する
* それぞれのbatchを並列スレッド上で処理する
* 全体のアルゴリズムを確認したければFigure 9.4を参照すること

```scala
import scala.concurrent.duration.Duration

def parallelFoldMap[A, B: Monoid]
      (values: Vector[A])
      (func: A => B): Future[B] = {
    // Calculate the number of items to pass to each CPU:
    val numCores = Runtime.getRuntime.availableProcessors 
    val groupSize = (1.0 * values.size / numCores).ceil.toInt

    // Create one group for each CPU:
    val groups: Iterator[Vector[A]] = values.grouped(groupSize)

    // Create a future to foldMap each group:
    val futures: Iterator[Future[B]] =
        groups map { group =>
            Future {
                group.foldLeft(Monoid[B].empty)(_ |+| func(_))
            }
        }

    // foldMap over the groups to calculate a final result:
    Future.sequence(futures) map { iterable =>
        iterable.foldLeft(Monoid[B].empty)(_ |+| _)
    } 
}

val result: Future[Int] = parallelFoldMap((1 to 1000000).toVector)(identity)

Await.result(result, 1.second)
// res19: Int = 1784293664
```

foldMap再利用版

```scala
def parallelFoldMap[A, B: Monoid]
    (values: Vector[A])
    (func: A => B): Future[B] = {
    val numCores = Runtime.getRuntime.availableProcessors val groupSize = (1.0 * values.size / numCores).ceil.toInt
    val groups: Iterator[Vector[A]] = values.grouped(groupSize)

    val futures: Iterator[Future[B]] = 
        groups.map(group => Future(foldMap(group)(func)))

    Future.sequence(futures) map { iterable =>
        iterable.foldLeft(Monoid[B].empty)(_ |+| _)
    }
}

val result: Future[Int] = parallelFoldMap((1 to 1000000).toVector)(identity)

Await.result(result, 1.second)
// res21: Int = 1784293664
```

## 9.3.4 parallelFoldMap with more Cats

```scala
import cats.Monoid
import cats.Foldable
import cats.Traverse

import cats.instances.int._ // for Monoid
import cats.instances.future._ // for Applicative and Monad
import cats.instances.vector._ // for Foldable and Traverse

import cats.syntax.semigroup._ // for |+|
import cats.syntax.foldable._ // for combineAll and foldMap 
import cats.syntax.traverse._ // for traverse

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

def parallelFoldMap[A, B: Monoid]
        (values: Vector[A])
        (func: A => B): Future[B] = {
    val numCores = Runtime.getRuntime.availableProcessors 
    val groupSize = (1.0 * values.size / numCores).ceil.toInt

    values
        .grouped(groupSize)
        .toVector
        .traverse(group => Future(group.toVector.foldMap(func))) 
        .map(_.combineAll)
}

val future: Future[Int] = parallelFoldMap((1 to 1000).toVector)(_ * 1000)

Await.result(future, 1.second)
// res3: Int = 500500000
```

## 9.4 Summary

* このケーススタディでは、クラスタで実行されるmap-reduceを模倣したシステムを実装した
* 我々のアルゴリズムは以下の3ステップに分かれる

1. データをバッチに分割し、1バッチをそれぞれの"ノード"に送信する
2. それぞれのバッチに対してローカルでmap-reduceを実行する
3. Monoidの加法演算で結果をcombineする

* 我々のなんちゃってシステムは現実のHadoopのようなmap-reduceシステムのバッチ処理をエミュレートする
* ところが実際には我々のすべての作業はノード間の通信が無視できる程度の単一のマシン上で行われている
* リストの効率的な並列処理のために、実際にはデータをバッチ処理する必要はなく、シンプルにFunctorを使ってmapして結果をMonoidでreduceすればよい

* バッチ戦略に関わらず、Monoidを使ったmap-reduceは加算や文字列結合に限らないパワフルで一般的なフレームワークである
* データサイエンティストの日々の分析タスクの大半はMonoidが適用できる
* 以下のすべてにMonoidがある
  * Bloomフィルタのような近似セット
  * HyperLogLogアルゴリズムなどのカーディナリティ推定
  * 確率的勾配降下のようなベクトルとベクトル操作
  * t-ダイジェストのような分位推定
