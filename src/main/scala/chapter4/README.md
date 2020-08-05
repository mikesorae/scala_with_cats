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

* はじめてScalaでflatMapに出会うとList上の便利なIteratingのパターンだと考えがち
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

このコードがコンパイルに失敗するのは

1. accumuratorのTypeをEitherではなくRightと推論する
2. Right.applyの型パラメータを指定していないのでLeftのパラメータがNoneで推論される

asRightを使うとRightではなくEitherを返してくれる他、1つの型パラメータを指定できるようになるのでこの問題が解決できる。

```scala
def countPositive(nums: List[Int]) = 
  nums.foldLeft(0.asRight[String]) { (accumulator, num) =>
    if(num > 0) {
      accumulator.map(_ + 1)
    } else {
      Left("Negative. Stopping!")
    } 
  }
countPositive(List(1, 2, 3))
// res5: Either[String,Int] = Right(3)

countPositive(List(1, -2, 3))
// res6: Either[String,Int] = Left(Negative. Stopping!)
```


`cats.syntax.either`はEitherのcompanionオブジェクトに便利なメソッドを追加する。

`catchOnly` や `catchNonFatal` は例外を補足するとても便利なEitherインスタンスを生成する。

```scala
Either.catchOnly[NumberFormatException]("foo".toInt)
// res7: Either[NumberFormatException,Int] = Left(java.lang. NumberFormatException: For input string: "foo")

Either.catchNonFatal(sys.error("Badness"))
// res8: Either[Throwable,Nothing] = Left(java.lang.RuntimeException: Badness)
```

その他のデータ型からEitherを生成するメソッドもある。

```scala
Either.fromTry(scala.util.Try("foo".toInt))
// res9: Either[Throwable,Int] = Left(java.lang.NumberFormatException: For input string: "foo")

Either.fromOption[String, Int](None, "Badness") 
// res10: Either[String,Int] = Left(Badness)
```

### 4.4.3 Transforming Eithers

`cats.syntax.either` は他にも便利なインスタンス生成のメソッドを提供する。
`orElse` や `getOrElse` を使って右側やデフォルト値を取り出すことができる。

```scala
import cats.syntax.either._

"Error".asLeft[Int].getOrElse(0)
// res11: Int = 0

"Error".asLeft[Int].orElse(2.asRight[String]) 
// res12: Either[String,Int] = Right(2)
```

`ensure` を使うと右側の値がPredicateを満たすかどうかを判定することができる。

```scala
 -1.asRight[String].ensure("Must be non-negative!")(_ > 0) 
 // res13: Either[String,Int] = Left(Must be non-negative!)
 ```

`recover`、`recoverWith` はFutureと同じようなエラーハンドリングを提供する。

```scala
"error".asLeft[Int].recover {
  case str: String => -1
}
// res14: Either[String,Int] = Right(-1)

"error".asLeft[Int].recoverWith {
  case str: String => Right(-1)
}
// res15: Either[String,Int] = Right(-1)
```

mapを補完する `leftMap`、`bimap` もある。

```scala
"foo".asLeft[Int].leftMap(_.reverse)
// res16: Either[String,Int] = Left(oof)

6.asRight[String].bimap(_.reverse, _ * 7)
// res17: Either[String,Int] = Right(42)

"bar".asLeft[Int].bimap(_.reverse, _ * 7)
// res18: Either[String,Int] = Left(rab)
```

`swap` メソッドを使うと右側と左側を入れ替えることができる。

```
123.asRight[String]
// res19: Either[String,Int] = Right(123)

123.asRight[String].swap
// res20: scala.util.Either[Int,String] = Left(123)
```

Catsは `toOption`, `toList`, `toTry`, `toValidated` などの変換メソッドも提供している。

### 4.4.4 Error Handling

* Eitherは一般的にfail-fastなエラーハンドリングで使われる
* flatMapで計算を繋げ、途中で計算が失敗すると残りの計算は実行されない

```scala
for {
  a <- 1.asRight[String]
  b <- 0.asRight[String]
  c <- if(b == 0) "DIV0".asLeft[Int]
       else (a / b).asRight[String]
} yield c * 100
// res21: scala.util.Either[String,Int] = Left(DIV0)
```

* Eitherを使うとき、我々はエラーをどんな型で表現するか決めないといけない

```scala
  type Result[A] = Either[Throwable, A]
```

* scala.util.Tryに似たセマンティクスを提供する
* Throwableだと型の範囲が広すぎて、どんなエラーがおきたのかわからない
* 他には代数的データ型で独自のエラーを表現するアプローチがある

```scala
sealed trait LoginError extends Product with Serializable 
final case class UserNotFound(username: String) extends LoginError
final case class PasswordIncorrect(username: String) extends LoginError

case object UnexpectedError extends LoginError

case class User(username: String, password: String)

type LoginResult = Either[LoginError, User]
```

* Throwableで見たような問題を解決できる
* 決められたエラーのセットとそれ以外を扱えるようになる
* パターンマッチで安全にエラーチェックができる

```scala
// Choose error-handling behaviour based on type:
def handleError(error: LoginError): Unit =
  error match {
    case UserNotFound(u) =>
      println(s"User not found: $u")
    case PasswordIncorrect(u) =>
      println(s"Password incorrect: $u")
    case UnexpectedError =>
      println(s"Unexpected error")
}
val result1: LoginResult = User("dave", "passw0rd").asRight
// result1: LoginResult = Right(User(dave,passw0rd))

val result2: LoginResult = UserNotFound("dave").asLeft
// result2: LoginResult = Left(UserNotFound(dave))

result1.fold(handleError, println)
// User(dave,passw0rd)

result2.fold(handleError, println)
// User not found: dave
```

### 4.4.5 Exercise: What is Best?

上記のサンプルのエラーハンドリング戦略はすべての目的に適しているだろうか。

エラーハンドリングでは他にどんな機能が必要になるだろうか。

オープン・クエスチョンなので例だけあげておく。

* Error recovery is important when processing large jobs. We don’t want to run a job for a day and then find it failed on the last element.

* Error reporting is equally important. We need to know what went wrong, not just that something went wrong.

* In a number of cases, we want to collect all the errors, not just the first one we encountered. A typical example is validating a web form. It’s a far better experience to report all errors to the user when they submit a form than to report them one at a time.


## 4.5 Aside: Error Handling and MonadError

* Catsは `MonadError` という型クラスを提供する
* MonadError は Either のようなエラーハンドリングのための抽象
* MonadError は エラー発生とエラーハンドリングの操作を提供する

### This Section is Optional!

* エラーハンドリングのMonadを抽象化したいケース以外ではMonadErrorを使う必要はない
  * e.g. FutureとTry、EitherとEitherT(Chapter5参照)の抽象化等

### 4.5.1 The MonadError Type Class

シンプルにしたMonadErrorの定義

```scala
package cats

trait MonadError[F[_], E] extends Monad[F] {
  // Lift an error into the `F` context:
  def raiseError[A](e: E): F[A]

  // Handle an error, potentially recovering from it:
  def handleError[A](fa: F[A])(f: E => A): F[A]

  // Test an instance of `F`,
  // failing if the predicate is not satisfied:
  def ensure[A](fa: F[A])(e: E)(f: A => Boolean): F[A]
}
```

MonadErrorは2つの型パラメータを持つ。

* FはErrorを扱うMonadの型
* EはFのMonadが扱うErrorの型

```scala
import cats.MonadError
import cats.instances.either._ // for MonadError

type ErrorOr[A] = Either[String, A]
val monadError = MonadError[ErrorOr, String]
```

### ApplicativeError

* 実はMonadErrorは `ApplicativeError` 型クラスをextendsしている
* ApplicativeErrorはChapter6でやる
* セマンティクスは他の型クラスと同じなので、今は詳細は飛ばす

### 4.5.2 Raising and Handling Errors

* 重要なメソッドは `raiseError` と `handleError`
* raiseErrorは、失敗を表すインスタンスを生成することを除けばMonadのpureのようなもの

```scala
val success = monadError.pure(42)
// success: ErrorOr[Int] = Right(42)

val failure = monadError.raiseError("Badness")
// failure: ErrorOr[Nothing] = Left(Badness)
```

* handleErrorはraiseErrorを補うもの
* エラーを受け取って成功に変換する(変換しないこともある)
* Futureのrecoverメソッドに似ている

```scala
monadError.handleError(failure) {
  case "Badness" =>
    monadError.pure("It's ok")
  case other =>
    monadError.raiseError("It's not ok")
}
// res2: ErrorOr[ErrorOr[String]] = Right(Right(It's ok))
```

* もう一つ便利なメソッドとして、filter的な振る舞いをする `ensure` がある

```scala
import cats.syntax.either._ // for asRight

monadError.ensure(success)("Number too low!")(_ > 1000) // res3: ErrorOr[Int] = Left(Number too low!)
```

* `raiseError` と `handleError` のsyntaxは `cats.syntax.applicativeError`
* `ensure` のsyntaxは `cats.syntax.monadError`

```scala
import cats.syntax.applicative._ // for pure
import cats.syntax.applicativeError._ // for raiseError etc import cats.syntax.monadError._ // for ensure 

val success = 42.pure[ErrorOr]
// success: ErrorOr[Int] = Right(42)

val failure = "Badness".raiseError[ErrorOr, Int]
// failure: ErrorOr[Int] = Left(Badness)

success.ensure("Number to low!")(_ > 1000)
// res4: Either[String,Int] = Left(Number to low!)
```

他にも便利なメソッドが沢山あるので、`cats.MonadError`、`cats.ApplicativeError`を参照


### 4.5.3 Instances of MonadError

* CatsはEither、Future、Tryなど様々なデータ型に対するMonadErrorインスタンスを用意している
* Eitherのエラー型はカスタマイズ可能だが、FutureとTryは常にThrowableを返す

```scala
import scala.util.Try
import cats.instances.try_._ // for MonadError

val exn: Throwable =
  new RuntimeException("It's all gone wrong")

exn.raiseError[Try, Int]
// res6: scala.util.Try[Int] = Failure(java.lang.RuntimeException: It' s all gone wrong)
```

## 4.6 The Eval Monad

* `cats.Eval` は今までとは違った(?) Evaluation の抽象化のためのMonad
* 一般的にはeagerとlazyの2つのモデルがある
* Evalではさらに、結果がメモ化されるかどうかの違いも提供する

### 4.6.1 Eager, Lazy, Memoized, Oh My!

* Eagerの計算はすぐさま実行されるけど、Lazyはアクセスされたときに実行される
* メモ化された計算は最初だけ実行されて、それ以降はキャッシュされる

* 例えばScalaのvalはeagerだしメモ化されている
* 以下の例では、目に見える副作用を使ってそれを確認することができる

```scala
val x = {
  println("Computing X")
  math.random
}
// Computing X
// x: Double = 0.013533499657218728

x // first access
// res0: Double = 0.013533499657218728

x // second access
// res1: Double = 0.013533499657218728
```

これに対して、defはlazyかつ非メモ化である。

```scala
def y = {
  println("Computing Y")
  math.random
}
// y: Double

y // first access
// Computing Y
// res2: Double = 0.5548281126990907

y // second access
// Computing Y
// res3: Double = 0.7681777032036599
```

最後にもう一つ、lazy valはlazyかつメモ化である。

```scala
lazy val z = {
  println("Computing Z")
  math.random
}
// z: Double = <lazy>

z // first access
// Computing Z
// res4: Double = 0.45707125364871903

z // second access
// res5: Double = 0.45707125364871903
```

### 4.6.2 Eval’s Models of Evaluation

* Evalには `Now`、`Later`、`Always`の3つのサブタイプがある
* それぞれコンストラクタメソッドがある

```scala
import cats.Eval

val now = Eval.now(math.random + 1000)
// now: cats.Eval[Double] = Now(1000.337992547842)

val later = Eval.later(math.random + 2000)
// later: cats.Eval[Double] = cats.Later@37f34fd2

val always = Eval.always(math.random + 3000)
// always: cats.Eval[Double] = cats.Always@486516b
```

valueメソッドで値を取り出すことができる。

```scala
now.value
// res6: Double = 1000.337992547842

later.value
// res7: Double = 2000.863079768816

always.value
// res8: Double = 3000.710688646907
```

* nowはvalと大体同じ
* eager + memoized

```scala
val x = Eval.now {
  println("Computing X")
  math.random
}
// Computing X
// x: cats.Eval[Double] = Now(0.5415551857150346)

x.value // first access
// res9: Double = 0.5415551857150346

x.value // second access
// res10: Double = 0.5415551857150346
```

* alwaysはdefと大体同じ
* lazy + not memoized

```scala
val y = Eval.always {
  println("Computing Y")
  math.random
}
// y: cats.Eval[Double] = cats.Always@3289cc05

y.value // first access
// Computing Y
// res11: Double = 0.06355685569536818

y.value // second access
// Computing Y
// res12: Double = 0.27425753581857903
```

* laterはlazy valと大体同じ
* lazy + memoized

```scala
val z = Eval.later {
  println("Computing Z")
  math.random
}
// z: cats.Eval[Double] = cats.Later@7a533449

z.value // first access
// Computing Z
// res13: Double = 0.3819703252438429

z.value // second access
// res14: Double = 0.3819703252438429
```

|scala|Cats|Properties|
|-|-|-|
|val|Now|eager, memoized|
|lazy val|Later|lazy, memoized|
|def|Always|lazy, not memoized|

### 4.6.3 Eval as a Monad

* 他のMonadと同じようにmap/flatMapでchainできる
* valueを呼ぶまで計算は実行されない

```scala
val greeting = Eval.
  always { println("Step 1"); "Hello" }.
  map { str => println("Step 2"); s"$str world" }
// greeting: cats.Eval[String] = cats.Eval$$anon$8@79ddd73b

greeting.value
// Step 1
// Step 2
// res15: String = Hello world
```

???

```scala
val ans = for {
  a <- Eval.now { println("Calculating A"); 40 }
  b <- Eval.always { println("Calculating B"); 2 }
} yield {
println("Adding A and B") a+b
}
// Calculating A
// ans: cats.Eval[Int] = cats.Eval$$anon$8@12da1eee

ans.value // first access
// Calculating B
// Adding A and B
// res16: Int = 42

ans.value // second access
// Calculating B
// Adding A and B
// res17: Int = 42
```

* Evalには計算結果をメモ化するための `memoize` メソッドがある
* memoizeを呼び出した手前まで(?)の計算がキャッシュされる

```scala
val saying = Eval.
  always { println("Step 1"); "The cat" }.
  map { str => println("Step 2"); s"$str sat on" }.
  memoize.
  map { str => println("Step 3"); s"$str the mat" }
// saying: cats.Eval[String] = cats.Eval$$anon$8@159a20cc

saying.value // first access
// Step 1
// Step 2
// Step 3
// res18: String = The cat sat on the mat

saying.value // second access
// Step 3
// res19: String = The cat sat on the mat
```

### 4.6.4 Trampolining and Eval.defer

* Evalの便利な性質の1つはmap/flatMapがトランポリン化されているところ
* つまり、スタックフレームが消費することなくmapやflatMapをネストすることができる
* 我々はこの性質を "stack safety" と呼ぶ

階乗のサンプル

```scala
 def factorial(n: BigInt): BigInt =
  if(n == 1) n else n * factorial(n - 1)
```

これは簡単にスタックオーバーフローさせることができる。

```scala
factorial(50000)
// java.lang.StackOverflowError
//   ...
```

Evalを使うとstack safeに書き直すことができる。

```scala
def factorial(n: BigInt): Eval[BigInt] =
  if(n == 1) {
    Eval.now(n)
  } else {
    factorial(n - 1).map(_ * n)
  }

factorial(50000).value
// java.lang.StackOverflowError
//   ...
```

* しかしこれはスタックオーバーフロー
* mapメソッド手前でfactorialがすでに再帰してるから
* ワークアラウンドとして `Eval.defer` を使うことができる

```scala
def factorial(n: BigInt): Eval[BigInt] =
  if(n == 1) {
    Eval.now(n)
  } else {
    Eval.defer(factorial(n - 1).map(_ * n))
  }

factorial(50000).value
// res20: BigInt = 334732050959714483691547609407148647791277322381045480773010032199016802214436564
```

* Evalは巨大な計算とデータ構造をstack safeにやりたいときに便利
* ただし、トランポリン化が自由ではないことを心に留めておくこと
* Evalは計算オブジェクトのチェーンをヒープに保存することでスタックの消費を回避している
* スタックの上限は無いがヒープの上限は存在する

### 4.6.5 Exercise: Safer Folding using Eval

nativeのfoldRightをEvalでstack safeに書き換えてみよう。

```scala
def foldRight[A, B](as: List[A], acc: B)(fn: (A, B) => B): B =
  as match {
    case head :: tail =>
      fn(head, foldRight(tail, acc)(fn))
    case Nil =>
      acc
  }
```

```scala
import cats.Eval

def foldRightEval[A, B](as: List[A], acc: Eval[B])
    (fn: (A, Eval[B]) => Eval[B]): Eval[B] =
  as match {
    case head :: tail =>
      Eval.defer(fn(head, foldRightEval(tail, acc)(fn)))
    case Nil =>
      acc
  }
```

```scala
def foldRight[A, B](as: List[A], acc: B)(fn: (A, B) => B): B =
  foldRightEval(as, Eval.now(acc)) { (a, b) =>
    b.map(fn(a, _))
  }.value

foldRight((1 to 100000).toList, 0L)(_ + _)
// res22: Long = 5000050000
```

## 4.7 The Writer Monad

* `cats.data.Writer` は計算とログを一緒に持ち回せるようにするMonad
* メッセージやエラーやその他のデータの記録ができる
* 最後の計算結果と一緒にログを取り出すことができる

* Writer Monadの一般的なユースケースは、標準的なロギング技術だと出力途中に他のログが混ざってしまうようなmulti-thread化での連続した処理の記録がある
* Writer Monadでは計算結果にログが紐付けられているので、ログが混ざるのを気にすることなく計算を同時に実行することができる

### 4.7.1 Creating and Unpacking Writers

* `Writer[W, A]` は2つの値を運ぶ
* ログの型 `W` と、結果の型 `A`

```scala
import cats.data.Writer
import cats.instances.vector._ // for Monoid

Writer(Vector(
  "It was the best of times",
  "it was the worst of times"
), 1859)
// res0: cats.data.WriterT[cats.Id,scala.collection.immutable.Vector[String],Int] = WriterT((Vector(It was the best of times, it was the worst of times),1859))
```

* 実際にはコンソールには `Writer[Vector[String], Int]` ではなく `WriterT[Id, Vector[String], Int]` が出力される
* コードの再利用性のためWriterTを使っている
* WriterTは次の章で出てくる `monad transformer` の一例
* WriterはWriterTのaliasである
* 一旦気にせず Writer[W, A] だと思って読みすすめる

```scala
type Writer[W, A] = WriterT[Id, W, A]
```

* 利便性のため、Catsはログと結果のためのコンストラクタだけを提供している
* 結果だけしか要らない場合はpureを使う
* empty logを作るためにはMonoid[W]がスコープにある必要がある

```scala
import cats.instances.vector._   // for Monoid
import cats.syntax.applicative._ // for pure

type Logged[A] = Writer[Vector[String], A]

123.pure[Logged]
// res2: Logged[Int] = WriterT((Vector(),123))
```

* ログだけあって結果がない場合は `cats.syntax.writer` の `tell` を使う

```scala
import cats.syntax.writer._ // for tell

Vector("msg1", "msg2", "msg3").tell
// res3: cats.data.Writer[scala.collection.immutable.Vector[String], Unit] = WriterT((Vector(msg1, msg2, msg3),()))
```

* ログも結果もあるときは `cats.syntax.writer` の `writer` メソッドか `Writer.apply` が使える

```scala
import cats.syntax.writer._ // for writer

val a = Writer(Vector("msg1", "msg2", "msg3"), 123)
// a: cats.data.WriterT[cats.Id,scala.collection.immutable.Vector[String],Int] = WriterT((Vector(msg1, msg2, msg3),123))

val b = 123.writer(Vector("msg1", "msg2", "msg3"))
// b: cats.data.Writer[scala.collection.immutable.Vector[String],Int] = WriterT((Vector(msg1, msg2, msg3),123))
```

* 結果を取り出すときは `value`
* ログを取り出すときは `written`

```scala
val aResult: Int =
  a.value
// aResult: Int = 123

val aLog: Vector[String] =
  a.written
// aLog: Vector[String] = Vector(msg1, msg2, msg3)
```

* `run` を使うとTupleで両方取り出せる

```scala
val (log, result) = b.run
// log: scala.collection.immutable.Vector[String] = Vector(msg1, msg2, msg3)
// result: Int = 123
```

### 4.7.2 Composing and Transforming Writers

* Writerのmap/flatMapは計算結果とログを元のWriterの結果に追加する
* Vectorのような追記、連結ができるデータ型を使うのが良い

```scala
val writer1 = for {
  a <- 10.pure[Logged]
  _ <- Vector("a", "b", "c").tell
  b <- 32.writer(Vector("x", "y", "z"))
} yield a + b
// writer1: cats.data.WriterT[cats.Id,Vector[String],Int] = WriterT((Vector(a, b, c, x, y, z),42))

writer1.run
// res4: cats.Id[(Vector[String], Int)] = (Vector(a, b, c, x, y, z) ,42)
```

* `mapWritten` を使うとログを加工できる

```scala
val writer2 = writer1.mapWritten(_.map(_.toUpperCase))
// writer2: cats.data.WriterT[cats.Id,scala.collection.immutable. Vector[String],Int] = WriterT((Vector(A, B, C, X, Y, Z),42))

writer2.run
// res5: cats.Id[(scala.collection.immutable.Vector[String], Int)] = ( Vector(A, B, C, X, Y, Z),42)
```

* ログと結果の両方を加工したいときは `bimap` を使う

```scala
val writer3 = writer1.bimap(
  log => log.map(_.toUpperCase),
  res => res * 100
)
// writer3: cats.data.WriterT[cats.Id,scala.collection.immutable. Vector[String],Int] = WriterT((Vector(A, B, C, X, Y, Z),4200))

writer3.run
// res6: cats.Id[(scala.collection.immutable.Vector[String], Int)] = ( Vector(A, B, C, X, Y, Z),4200)
val writer4 = writer1.mapBoth { (log, res) =>
  val log2 = log.map(_ + "!")
  val res2 = res * 1000
  (log2, res2)
}
// writer4: cats.data.WriterT[cats.Id,scala.collection.immutable. Vector[String],Int] = WriterT((Vector(a!, b!, c!, x!, y!, z!) ,42000))

writer4.run
// res7: cats.Id[(scala.collection.immutable.Vector[String], Int)] = ( Vector(a!, b!, c!, x!, y!, z!),42000)
```

* `reset` でログのリセットができる
* `swap` で結果とログが入れ替えられる

```scala
val writer5 = writer1.reset
// writer5: cats.data.WriterT[cats.Id,Vector[String],Int] = WriterT((Vector(),42))

writer5.run
// res8: cats.Id[(Vector[String], Int)] = (Vector(),42)
val writer6 = writer1.swap
// writer6: cats.data.WriterT[cats.Id,Int,Vector[String]] = WriterT((42,Vector(a, b, c, x, y, z)))

writer6.run
// res9: cats.Id[(Int, Vector[String])] = (42,Vector(a, b, c, x, y, z) )
```

### 4.7.3 Exercise: Show Your Working

TODO


## 4.8 The Reader Monad

* `cats.data.Reader` はなんらかの入力に依存する連続した計算のためのMonad

* よくある使い方としてはdependency injectionがある
* 複数の外部設定に依存した操作がいくつかあるとき、Reader Monadでそれらをchainして1つの大きな操作にすることができる
* パラメータとして1つの設定を受け取り、順序の指定ができる

### 4.8.1 Creating and Unpacking Readers

* `Reader.apply` を使って関数 `A => B` から `Reader[A, B]` を作ることができる

```scala
import cats.data.Reader

case class Cat(name: String, favoriteFood: String)
// defined class Cat

val catName: Reader[Cat, String] =
  Reader(cat => cat.name)
// catName: cats.data.Reader[Cat,String] = Kleisli(<function1>)
```

* `Reader.run` でCatに関数を適用する

```scala
 catName.run(Cat("Garfield", "lasagne"))
// res0: cats.Id[String] = Garfield
```

#### 4.8.2 Composing Readers

* Readerモナドの真の力は map / flatMap で異なる種類の関数を合成するときに発揮される
* 一般的に、同じタイプのconfigurationを受け取るReaderのセットを定義し、map / flatMap で繋げ、最後に run を呼んで設定をinjectする

map メソッドは単に、その結果を関数に渡すことでReaderの計算を拡張する。

```scala
val greetKitty: Reader[Cat, String] =
  catName.map(name => s"Hello ${name}")

greetKitty.run(Cat("Heathcliff", "junk food")) // res1: cats.Id[String] = Hello Heathcliff
```

* flatMapを使うと、同じ入力型に依存する複数のReaderを結合できるようになる
* greetingに加えてfeedを追加したサンプルで例を示す。

```scala
val feedKitty: Reader[Cat, String] =
Reader(cat => s"Have a nice bowl of ${cat.favoriteFood}")

val greetAndFeed: Reader[Cat, String] =
  for {
    greet <- greetKitty
    feed  <- feedKitty
  } yield s"$greet. $feed."

greetAndFeed(Cat("Garfield", "lasagne"))
// res3: cats.Id[String] = Hello Garfield. Have a nice bowl of lasagne
.
greetAndFeed(Cat("Heathcliff", "junk food"))
// res4: cats.Id[String] = Hello Heathcliff. Have a nice bowl of junk food.
```

#### 4.8.3 Exercise: Hacking on Readers

* 典型的なReaderモナドの使い方は、configurationをパラメータとして受け取るプログラムの構築
* シンプルなログインシステムの例をやってみる
* Configurationは2つのデータベースから成り、それぞれ有効なユーザのリストとそのパスワードのリストを持つ

```scala
case class Db(
  usernames: Map[Int, String],
  passwords: Map[String, String]
)
```

* 最初にDbを受け取るReaderとして DbReader エイリアスを作る。
* これを定義しておくと以降のコードが短くなる

```scala
type DbReader[A] = Reader[Db, A]
```

* Int型の user ID から `username` を検索するのと、String型の username からパスワードを検索するための DbReader を生成するメソッドを作成する

```scala
def findUsername(userId: Int): DbReader[Option[String]] =
  Reader(db => db.usernames.get(userId))

def checkPassword(
      username: String,
      password: String): DbReader[Boolean] =
  Reader(db => db.passwords.get(username).contains(password))
```

* 最後に、与えられた username と password をチェックする `checkLogin` メソッドを作る

```scala
def checkLogin(
      userId: Int,
      password: String): DbReader[Boolean] =
  for {
    username   <- findUserName(userId)
    passwordOk <- username.map { username =>
      checkPassword(username, password)
    }.getOrElse {
      false.pure[DbReader]
    }
  } yield passwordOk
```

これを使うと以下のようにログインチェックができるようになる

```scala
val users = Map(
  1 -> "dade",
  2 -> "kate",
  3 -> "margo"
)

val passwords = Map(
  "dade"  -> "zerocool",
  "kate"  -> "acidburn",
  "margo" -> "secret"
)

val db = Db(users, passwords)

checkLogin(1, "zerocool").run(db)
// res10: cats.Id[Boolean] = true

checkLogin(4, "davinci").run(db)
// res11: cats.Id[Boolean] = false
```

#### 4.8.4 When to Use Readers?

* Readerモナドは Dependency Injection を行うツールの1つ
* 依存性をinputとして受け取る関数を構築して map / flatMapで chain できる
* ScalaにはDIを実現するための様々な方法がある
  * 複数のParameterリストを受け取るメソッド
  * implicitパラメータや型クラスの経由
  * cake pattern
  * その他のDIフレームワーク
* Readerは以下のようなシチュエーションで最も役に立つ
  * 関数で簡単に表現できるようなバッチプログラムの構築時
  * パラメータのインジェクションを遅延させる必要がある場合
  * プログラムの部品をテストできるようにする必要がある場合
* もっと依存が多く複雑な場合や、プログラムが純粋な関数で表現できないような場合は、他のDIテクニックを使うのが適切だろう

#### Kleisli Arrows

* consoleでReaderが `Kleisli` という別の型で実装されていることに気づいただろう
* Kleisli は Reader の更に一般的な形式で、結果型の型コンストラクタを一般化する
* Chapter5で出てくる

### 4.9 The State Monad

* `cats.data.State` を使うと、計算の一部として状態をもち回せるようになる
* アトミックな状態操作を表現するSateインスタンスを定義し、map / flatMap でそれらをスレッド化するようにした
* これにより、mutable stateをmutateすることなく純粋関数的なやりかたで実現できる

#### 4.9.1 Creating and Unpacking State

* 要約すると `State[S, A]` は `S => (S, A)` という関数の型を表す
* S は State の型で、A は結果の型

```scala
import cats.data.State

val a = State[Int, String] { state =>
  (state, s"The state is $state")
}
// a: cats.data.State[Int,String] = cats.data.IndexedStateT@6ceace82
```

* 言い換えればStateインスタンスは以下の2つのことをやっている
  * 入力のStateを出力のStateに変形する
  * 結果を計算する

* 初期状態を渡すことでStateモナドを `run` することができる
* Stateモナドには異なる結果の組み合わせを返す `run`, `runS`, `runA` の3つのメソッドがある
* それぞれのメソッドはStackを安全に扱うためEvalインスタンスを返す

```scala
// Get the state and the result:
val (state, result) = a.run(10).value
// state: Int = 10
// result: String = The state is 10

// Get the state, ignore the result:
val state = a.runS(10).value
// state: Int = 10

// Get the result, ignore the state:
val result = a.runA(10).value
// result: String = The state is 10
```

余談
```scala
a: cats.data.State[Int,String] = cats.data.IndexedStateT@bce093db

scala> a.run(10)
res0: cats.Eval[(Int, String)] = cats.Eval$$anon$4@e576d26

scala> a.runS(20)
res1: cats.Eval[Int] = cats.Eval$$anon$1@c10d3d05

scala> a.runA(30)
res2: cats.Eval[String] = cats.Eval$$anon$1@2f2c1bde
```

#### 4.9.2 Composing and Transforming State

* Readerで見たのと同じように、Stateも map / flatMap で結合することにより力を発揮する

```scala
val step1 = State[Int, String] { num =>
  val ans = num + 1
  (ans, s"Result of step1: $ans")
}
// step1: cats.data.State[Int,String] = cats.data. IndexedStateT@76122894

val step2 = State[Int, String] { num =>
  val ans = num * 2
  (ans, s"Result of step2: $ans")
}
// step2: cats.data.State[Int,String] = cats.data. IndexedStateT@1eaaaa5d

val both = for {
  a <- step1
  b <- step2
} yield (a, b)
// both: cats.data.IndexedStateT[cats.Eval,Int,Int,(String, String)] = cats.data.IndexedStateT@47a10835

val (state, result) = both.run(20).value
// state: Int = 42
// result: (String, String) = (Result of step1: 21,Result of step2:
42)
```

* 最後の結果は両方のtransformationを順番に適用した結果
* for式で指定していないにも関わらず(?)、ステップごとにスレッド化される

* Stateモナドを使う一般的なモデルは、各計算ステップをインスタンスで表現し、標準的なモナド演算子で合成する
* Catsはprimitiveなステップを生成するための便利なコンストラクタを提供する
  * 値を受け取って(値, 値)を返す `get`
  * stateをupdateして(state, unit)を返す `set`
  * stateを無視して(値, result)を返す `pure`
  * stateにtransformを適用して(値, transform(値))を返す `inspect`
  * stateにupdateを適用して(transform(値), unit)を返す `modify`


```scala
val getDemo = State.get[Int]
// getDemo: cats.data.State[Int,Int] = cats.data.IndexedStateT@6ffe574a

getDemo.run(10).value
// res3: (Int, Int) = (10,10)

val setDemo = State.set[Int](30)
// setDemo: cats.data.State[Int,Unit] = cats.data.IndexedStateT@4168bec2

setDemo.run(10).value
// res4: (Int, Unit) = (30,())


val pureDemo = State.pure[Int, String]("Result")
// pureDemo: cats.data.State[Int,String] = cats.data.IndexedStateT@6812d576

pureDemo.run(10).value
// res5: (Int, String) = (10,Result)

val inspectDemo = State.inspect[Int, String](_ + "!") // inspectDemo: cats.data.State[Int,String] = cats.data.IndexedStateT@37c08614

inspectDemo.run(10).value
// res6: (Int, String) = (10,10!)


val modifyDemo = State.modify[Int](_ + 1)
// modifyDemo: cats.data.State[Int,Unit] = cats.data.IndexedStateT@4242cae6

modifyDemo.run(10).value
// res7: (Int, Unit) = (11,())
```

* これらのブロックをfor式を使って組み立てられる
* 一般的にstateの変更のためだけのtransformの結果は無視する

```scala
import State._

val program: State[Int, (Int, Int, Int)] = for {
  a <- get[Int]
  _ <- set[Int](a + 1)
  b <- get[Int]
  _ <- modify[Int](_ + 1)
c <- inspect[Int, Int](_ * 1000)
} yield (a, b, c)
// program: cats.data.State[Int,(Int, Int, Int)] = cats.data.IndexedStateT@22a799f8

val (state, result) = program.run(1).value
// state: Int = 3
// result: (Int, Int, Int) = (1,2,3000)
```

#### 4.9.3 Exercise: Post-Order Calculator

TODO


### 4.10 Defining Custom Monads

* モナドのメソッドを定義すればカスタムモナドを作ることができる
* 必要なのは flatMap / pure / tailRecM (これはまだ説明されてない)

Optionの例
```scala
import cats.Monad
import scala.annotation.tailrec

val optionMonad = new Monad[Option] {
  def flatMap[A, B](opt: Option[A])
      (fn: A => Option[B]): Option[B] =
    opt flatMap fn

  def pure[A](opt: A): Option[A] =
    Some(opt)

  @tailrec
  def tailRecM[A, B](a: A)
      (fn: A => Option[Either[A, B]]): Option[B] =
    fn(a) match {
      case None           => None
      case Some(Left(a1)) => tailRecM(a1)(fn)
      case Some(Right(b)) => Some(b)
    } 
}
```

* tailRecMはネストしたflatMap呼び出しのスタック消費を制限するためにCats内で使われる
* このテクニックはPureScriptの作者の Phil Freeman による以下の論文から来ている
  * http://functorial.com/stack-safety-for-free/index.pdf
* ResultがRightであるまで再帰的に呼び出される
* tailRecMが末尾再帰可能である限り、Section7.1出でてくるような大きなリストの再帰的なfoldの場合においてもCatsはstack safeを保証する
* 末尾再帰化できない場合はstack safeを保証できず、大きな計算の場合はStackOverflowErrorsを返す
* すべてのbuild-inのCatsのモナドは末尾再帰化されたtailRecMを持っている

#### 4.10.1 Exercise: Branching out Further with Monads

前の章でみたTreeデータ構造をモナドにしてみよう。

```scala
sealed trait Tree[+A]

final case class Branch[A](left: Tree[A], right: Tree[A])
  extends Tree[A]

final case class Leaf[A](value: A) extends Tree[A]

def branch[A](left: Tree[A], right: Tree[A]): Tree[A] =
  Branch(left, right)

def leaf[A](value: A): Tree[A] =
  Leaf(value)
```

non-tail-rec
```scala
import cats.Monad

implicit val treeMonad = new Monad[Tree] {
  def pure[A](value: A): Tree[A] =
    Leaf(value)

  def flatMap[A, B](tree: Tree[A])
      (func: A => Tree[B]): Tree[B] =
    tree match {
      case Branch(l, r) =>
        Branch(flatMap(l)(func), flatMap(r)(func)) 
      case Leaf(value) =>
        func(value)
    }

 def tailRecM[A, B](a: A)
     (func: A => Tree[Either[A, B]]): Tree[B] =
   flatMap(func(a)) {
     case Left(value) =>
       tailRecM(value)(func)
     case Right(value) =>
       Leaf(value)
   }
}
```

tail-rec
```scala
import cats.Monad

implicit val treeMonad = new Monad[Tree] {
  def pure[A](value: A): Tree[A] =
    Leaf(value)

  def flatMap[A, B](tree: Tree[A])
      (func: A => Tree[B]): Tree[B] =
    tree match {
      case Branch(l, r) =>
        Branch(flatMap(l)(func), flatMap(r)(func)) 
      case Leaf(value) =>
        func(value)
      }

  def tailRecM[A, B](arg: A)
      (func: A => Tree[Either[A, B]]): Tree[B] = {
    @tailrec
    def loop(
          open: List[Tree[Either[A, B]]],
          closed: List[Option[Tree[B]]]): List[Tree[B]] =
      open match {
        case Branch(l, r) :: next =>
          loop(l :: r :: next, None :: closed)
        case Leaf(Left(value)) :: next =>
          loop(func(value) :: next, closed)
        case Leaf(Right(value)) :: next =>
          loop(next, Some(pure(value)) :: closed)
        case Nil =>
          closed.foldLeft(Nil: List[Tree[B]]) { (acc, maybeTree) =>
            maybeTree.map(_ :: acc).getOrElse {
              val left :: right :: tail = acc
              branch(left, right) :: tail
            }
          }
      }
    loop(List(func(arg)), Nil).head
  }
}

### 4.11 Summary

* モナドの詳細について見てきた
* flatMapは連続した計算に対し、次にどの操作が発生するかを記述する演算子として見ることができる
* この見方から見ると
  * Optionはエラーメッセージなしで失敗しうる計算の表現
  * Eitherはエラーメッセージありで失敗しうる計算の表現
  * Listは複数の結果がありうる場合の表現
  * Futureはある未来の時点で値を生み出す計算の表現
* 他にもID, Reader, Writer, State等の例を見た
* 最後に、あんまりないだろうけどtailRecMを使ってカスタムモナドを定義する方法も学んだ
* Monadを理解するためにtailRecMを理解する必要はないが、monadicなコードを書くときに役立つ
