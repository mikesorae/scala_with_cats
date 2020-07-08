# Scala with Cats 4章

* Monadとはざっくりいうとコンストラクタ(pure/return)とflatMapを持った何か
* 前章で見たOption/List/FutureはMonadでもある
* Scalaの標準ライブラリには `flatMapできる何か` を表す概念が不足しているので、この型クラスが使えることもCatsを使う利点の1つ

## 4.1 What is a Monad?

いろんな例えがあるけど、シンプルに言いなおしてみるなら以下の通り。

> A monad is a mechanism for sequencing computations.

Monadは連続した計算のためのメカニズムである。

* Section 3.1 でfunctorのときも同じようなことを言っていた？
  * functorでは最初の一回にしか使えない
  * 各ステップのそれ以上の複雑さは考慮しない
* MonadのflatMapを使うと中間のステップの複雑さが考慮できるようになる
  * OptionのflatMapでは計算の中間のOptionsを扱えるようになる
  * ListのflatMapでは計算の中間のListsを扱えるようになる
* flatMapに渡される各functionは計算の中のapplication-specificな部分だけ
* flatMap自体が次のflatMapにつなげるための複雑さを受け持ってくれる

### Options

Optionを使うと `値を返すかもしれないし、返さないかもしれない` 計算を繋げられるようになる。

```scala
def parseInt(str: String): Option[Int] =
  scala.util.Try(str.toInt).toOption

def divide(a: Int, b: Int): Option[Int] =
  if(b == 0) None else Some(a / b)
```

```scala
def stringDivideBy(aStr: String, bStr: String): Option[Int] =
  parseInt(aStr).flatMap { aNum =>
    parseInt(bStr).flatMap { bNum =>
      divide(aNum, bNum)
    } 
  }
```

* `flatMap :: Option[A] -> (A -> Option[B]) -> Option[B]`
* 計算の各ステップでflatMapはfunctionを呼ぶかどうかを選ぶ(値があれば呼ぶ)
* どこかでNoneになったら全体もNoneになるというfail-fastなエラーハンドリングになる

* MonadはFunctorでもある
* 新しいMonadを導入してもしなくてもflatMapとmapを頼ることができる(?)
* flatMapもmapもfor内包表記を使うと連続した計算をわかりやすく書ける

```scala
def stringDivideBy(aStr: String, bStr: String): Option[Int] = for {
    aNum <- parseInt(aStr)
    bNum <- parseInt(bStr)
    ans  <- divide(aNum, bNum)
} yield ans
```

### Lists

* はじめてScalaでflatMapに出会うとList上の便利なIteratingのパターンだ考えがち
* Listのモナディックな振る舞いに着目すると別の考え方がある
* Listsを中間成果物のセットだと考えると、flatMapは順序と組み合わせを計算する構造だと考えることができる
    * 以下のコードの例でいうと、x = 3, y = 2で6通りの組み合わせがある
    * flatMapは中間成果物のListから組み合わせを生成している

```scala
for {
  x <- (1 to 3).toList
  y <- (4 to 5).toList
} yield (x, y)
// res5: List[(Int, Int)] = List((1,4), (1,5), (2,4), (2,5), (3,4), (3,5))
```

※ `(1 to 3).toList.flatMap {x => (4 to 5).toList.map {y => (x,y)}}` と等価

### Futures

Futureは非同期を気にすることなく計算を連続させるMonadである。

```scala
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

def doSomethingLongRunning: Future[Int] = ???
def doSomethingElseLongRunning: Future[Int] = ???

def doSomethingVeryLongRunning: Future[Int] =
  for {
    result1 <- doSomethingLongRunning
    result2 <- doSomethingElseLongRunning
  } yield result1 + result2
```

* スレッドプールやスケジューラといった裏側の複雑さを気にしなくて良い
* 次の計算は前の計算の結果を待ってから実行される
* もちろんFutureを並列に実行させることもできるが、それはまた別の機会に


### 4.1.1 Definition of a Monad

* flatMapについてだけ話したが、モナディックな振る舞いは形式的には以下の2つの操作からなる
    * pure, of type A => F[A];
    * flatMap1, of type (F[A], A => F[B]) => F[B].
* pureはPlainな値からMonadの値を生成する
* flatMapはMonadのコンテキストから値を取り出して次の計算のコンテキストを生成する

CatsでのMonadの定義は以下のようになる。

```scala
import scala.language.higherKinds

trait Monad[F[_]] {
  def pure[A](value: A): F[A]
  def flatMap[A, B](value: F[A])(func: A => F[B]): F[B]
}
```

### モナド則

#### 左単位元
```scala
pure(a).flatMap(func) == func(a)
```

#### 右単位元
```scala
m.flatMap(pure) == m
```

#### 結合律
```scala
m.flatMap(f).flatMap(g) == m.flatMap(x => f(x).flatMap(g))
```

### 4.1.2 Exercise: Getting Func-y

すべてのMonadはFunctorでもある。
flatMapとpureを使ってmapを再定義してみよう。

```scala
import scala.language.higherKinds

trait Monad[F[_]] {
  def pure[A](a: A): F[A]
  def flatMap[A, B](value: F[A])(func: A => F[B]): F[B]
  def map[A, B](value: F[A])(func: A => B): F[B] =
    flatMap(value) { x => pure(func(x)) }
}
```


## 4.2 Monads in Cats

### 4.2.1 The Monad Type Class

* CatsのMonadの型クラスはcats.Monad
* cats.MonadはFlatMapとApplicativeの2つの型クラスをextendsしている
* ApplicativeもまたFunctorをextendsしている
    * ApplicativeについてはChapter6で

```scala
import cats.Monad

import cats.instances.option._ // for Monad
import cats.instances.list._   // for Monad

val opt1 = Monad[Option].pure(3)
// opt1: Option[Int] = Some(3)

val opt2 = Monad[Option].flatMap(opt1)(a => Some(a + 2)) 
// opt2: Option[Int] = Some(5)

val opt3 = Monad[Option].map(opt2)(a => 100 * a)
// opt3: Option[Int] = Some(500)

val list1 = Monad[List].pure(3)
// list1: List[Int] = List(3)

val list2 = Monad[List].
  flatMap(List(1, 2, 3))(a => List(a, a*10))
// list2: List[Int] = List(1, 10, 2, 20, 3, 30)

val list3 = Monad[List].map(list2)(a => a + 123)
// list3: List[Int] = List(124, 133, 125, 143, 126, 153)
```

### 4.2.2 Default Instances

CatsはすべてのMonadについて、標準ライブラリにあるOption、List、Vectorなどのインスタンスを `cats.instances` で提供している。

```scala
import cats.instances.option._ // for Monad 

Monad[Option].flatMap(Option(1))(a => Option(a*2))
// res0: Option[Int] = Some(2)

import cats.instances.list._ // for Monad

Monad[List].flatMap(List(1, 2, 3))(a => List(a, a*10))
// res1: List[Int] = List(1, 10, 2, 20, 3, 30)

import cats.instances.vector._ // for Monad

Monad[Vector].flatMap(Vector(1, 2, 3))(a => Vector(a, a*10))
// res2: Vector[Int] = Vector(1, 10, 2, 20, 3, 30)
```

* CatsはFuture Monadも提供している
* CatsのFutureのpureやflatMapは、標準のFutureと違ってExecutionContextを受け取らない
    * 何故ならばそれはMonadのtraitの定義にないから
* その代わりに、CatsのFutureはMonadを呼び出すときにExecutionContextを要求する

```scala
import cats.instances.future._ // for Monad

import scala.concurrent._
import scala.concurrent.duration._

val fm = Monad[Future]
// <console>:37: error: could not find implicit value for parameter
instance: cats.Monad[scala.concurrent.Future] // val fm = Monad[Future]
// ^
```

```scala
import scala.concurrent.ExecutionContext.Implicits.global

val fm = Monad[Future]
// fm: cats.Monad[scala.concurrent.Future] = cats.instances.
     FutureInstances$$anon$1@53c37657
```

Future Monadはその後のpureやflatMapの呼び出しでキャプチャしたExecutionContextを利用する。


### 4.2.3 Monad Syntax

* 毎回 `Monad[Option].flatMap` のように書くのは面倒
* 便利な構文が以下にある
    * `cats.syntax.flatMap`
        * flatMapが直接呼び出せるようになる
    * `cats.syntax.functor`
        * mapが直接呼び出せるようになる
    * `cats.syntax.applicative`
        * pureが直接呼び出せるようになる
* `cats.implisits` をimportすれば全部使えるようになるが、ここではわかりやすくするため1つずつインポートする

```scala
import cats.instances.option._   // for Monad
import cats.instances.list._     // for Monad
import cats.syntax.applicative._ // for pure

1.pure[Option]
// res4: Option[Int] = Some(1)

1.pure[List]
// res5: List[Int] = List(1)
```

OptionやListのflatMapやmapは標準メソッドがあってデモが難しいので、Monadでパラメータを受け取る汎用の関数を定義する。

```scala
import cats.Monad

import cats.syntax.functor._ // for map
import cats.syntax.flatMap._ // for flatMap
import scala.language.higherKinds

def sumSquare[F[_]: Monad](a: F[Int], b: F[Int]): F[Int] = 
  a.flatMap(x => b.map(y => x*x + y*y))

import cats.instances.option._ // for Monad
import cats.instances.list._   // for Monad

sumSquare(Option(3), Option(4))
// res8: Option[Int] = Some(25)

sumSquare(List(1, 2, 3), List(4, 5))
// res9: List[Int] = List(17, 26, 20, 29, 25, 34)
```

* for内包表記を使って書き直すこともできる

```scala
def sumSquare[F[_]: Monad](a: F[Int], b: F[Int]): F[Int] = 
  for {
    x <- a
    y <- b
  } yield x*x + y*y

sumSquare(Option(3), Option(4))
// res10: Option[Int] = Some(25)

sumSquare(List(1, 2, 3), List(4, 5))
// res11: List[Int] = List(17, 26, 20, 29, 25, 34)
```


## 4.3 The Identity Monad

```scala
import scala.language.higherKinds
import cats.Monad
import cats.syntax.functor._ // for map
import cats.syntax.flatMap._ // for flatMap
def sumSquare[F[_]: Monad](a: F[Int], b: F[Int]): F[Int] = for {
x <- a
    y <- b
  } yield x*x + y*y
```

* 前のセクションの例では、関数の引数はMonadでなければならず、Plainな値を渡すことはできない
* Monadな値もそうでない値も渡せたらとても便利では(ほんとか？)
* Catsはmonadicな値とnon-monadicな値のギャップを埋めるId型を提供している

```scala
import cats.Id

sumSquare(3 : Id[Int], 4 : Id[Int])
// res2: cats.Id[Int] = 25
```

Id型の定義は以下の通り。

```scala
package cats

type Id[A] = A
```

```scala
val a = Monad[Id].pure(3)
// a: cats.Id[Int] = 3

val b = Monad[Id].flatMap(a)(_ + 1)
// b: cats.Id[Int] = 4

import cats.syntax.functor._ // for map
import cats.syntax.flatMap._ // for flatMap

for {
  x <- a
  y <- b
} yield x + y
// res6: cats.Id[Int] = 7
```

* 本番ではFutureを使って非同期にしつつ、テストではIdを使って同期的に書いたりすることができる
* 実際の使い方はChapter8にて

### 4.3.1 Exercise: Monadic Secret Identities

Id Monadのpure、map、flatMapメソッドを実装してみよう。


## 4.4 Either

* Scala2.11までのEitherはmap、flatMapがないのでMonadではなかった
* Scala2.12から上記は解消された

### 4.4.1 Left and Right Bias

* Scala2.11までのEitherはright-biasedでもなかったので毎回 `.right` を書く必要があった

```scala
val either1: Either[String, Int] = Right(10)
val either2: Either[String, Int] = Right(32)

for {
  a <- either1.right
  b <- either2.right
} yield a + b
// res0: scala.util.Either[String,Int] = Right(42)
```

* 2.12からは `.right` なしで書けるようになった
* Catsはこの振る舞いを2.11以前向けに `cats.syntax.either` でバックポートした
* 2.12+ではこのimportを削除してもしなくても大丈夫

```scala
for {
  a <- either1
  b <- either2
} yield a + b
// res1: scala.util.Either[String,Int] = Right(42)
```


### 4.4.2 Creating Instances

* `cats.syntax.either` をimportすると `asRight`, `asLeft` が使えるようになる
* Left.apply / Right.applyを使うと LeftかRight型の戻り値を返すが、これは型推論のバグの原因になる
* asRight / asLeftは Either 型で返してくれるというアドバンテージがある

```scala
import cats.syntax.either._ // for asRight

val a = 3.asRight[String]
// a: Either[String,Int] = Right(3)

val b = 4.asRight[String]
// b: Either[String,Int] = Right(4)

for {
    x <- a
    y <- b
} yield x*x + y*y
// res4: scala.util.Either[String,Int] = Right(25)
```

```scala
def countPositive(nums: List[Int]) =
  nums.foldLeft(Right(0)) { (accumulator, num) =>
    if(num > 0) {
      accumulator.map(_ + 1)
    } else {
      Left("Negative. Stopping!")
    } 
}
// <console>:21: error: type mismatch;
// found : scala.util.Either[Nothing,Int] 
// required: scala.util.Right[Nothing,Int] 
//               accumulator.map(_ + 1)
//                              ^
// <console>:23: error: type mismatch;
// found : scala.util.Left[String,Nothing] 
// required: scala.util.Right[Nothing,Int] 
//               Left("Negative. Stopping!") 
//                    ^
```
