Scala with Cats ３章


# Functors

- 様々なコンテキスト（Option, List .. ) 下での処理のシーケンスを表現する概念
- monads and applica􏰀ve functors are some of the most commonly used abstrac􏰀ons in Cats.


## 3.1 Exmaples of Functors

- ざっくりいうと，functor は map メソッドを持つものすべて


#### map
- すべての要素に指定した関数を適用する処理
- 値は変えるが context を変えることはない
	- List の map は list の値を変えはするが，結果として返されるのはリスト
- context を変えるこことはないので，何度でも適用することが可能

### 3.2 More Examples

- Future にも同じ用に map が適用できる
- Function も functor と考えられる
	-  X -> A と変換する関数に対して A ー＞ B となる関数を適用したら最終的に X -> B となる関数が得られる
	- 関数の合成 みたいな
	- 関数 の map 処理（関数の合成） は Future のようなもの ( lazily queueing up opera􏰀ons similar to Future )
		- 前の関数の処理結果を用いて次の処理をすると定義
		- 入力値が決まってそれぞれの関数の処理がはじめて走る

### 3.3. Definition of a Functor

scala での functor の定義 ( 型クラス）

```
trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}
```


- Kinds
	- 型の型 のようなもの
	- 型の '穴' の数を説明するもの
	- type constructor は kinds の一種
		- ある型を入れると，それ自体がある型になるもの
		- 例：List, List は type constructor
			- List[Int] の用に何かの型をいれることができる
		- generic type と混同しないうように注意

```
List    // type constructor, takes one parameter
List[A] // type, produced using a type parameter
```


scala での type constructor の定義

```
// Declare F using underscores:
def myMethod[F[_]] = {
  // Reference F without underscores:
  val functor = Functor.apply[F]
  // ...
}
```

> Armed with this knowledge of type constructors, we can see that the Cats def- ini􏰀on of Functor allows us to create instances for any single-parameter type constructor, such as List, Option, Future, or a type alias such as MyFunc.

### 3.5 Functors in Cats

- cats での Functor の Type class, Interface Syntax, Type Class Instance の紹介
- map
	- map 処理を適用したいオブジェクト，適用する処理の２つを引数に取る
- lift
	- 処理が扱う型(入力，出力) を変換する
		-   入力，出力が同じ型となる処理である必要がある
	- TypeConstructor を Functor の 型として渡してあげることでその TypeConstructor を適用した型に，処理の 引数，出力の型を 変更できる
		- 引数 Int, 出力 Int  -> 引数 List[Int], 出力 : List[Int]
	- 関数に型変換の処理を map した結果 を返している
		- -> functor の処理といえる

```
val list2 = Functor[List].map(list1)(_ * 2)
// list2: List[Int] = List(2, 4, 6)


val func = (x: Int) => x + 1
// func: Int => Int = <function1>

val liftedFunc = Functor[Option].lift(func)
// liftedFunc: Option[Int] => Option[Int] = cats.Functor$$Lambda$5433

liftedFunc(Option(1))
// res1: Option[Int] = Some(2)
```


#### 3.5.1 Functor Type Class

- Functor の Type Class, Type Class Instance の利用例

#### 3.5.2 Functor Syntax

- Functor の Interface Syntax の利用例
	- .map メソッドを色んな型に追加する

関数に map メソッドを生やして，関数の合成を行う

```
import cats.instances.function._ // for Functor
import cats.syntax.functor._     // for map

val func1 = (a: Int) => a + 1
val func2 = (a: Int) => a * 2
val func3 = (a: Int) => s"${a}!"

// 関数を合成
val func4 = func1.map(func2).map(func3)

func4(123)
// res2: String = "248!"
```


#### 3.5.3 Instances for Custom Types

- 簡単な Type Class Instance の実装例紹介
- 独自の型に拡張する方法を紹介
	- Future に対して  functor となる Type class instance を定義する場合, Future 自体に ExecutionContext オブジェクトを implicit に渡さなくてはいけないので以下のように書くと


```
import scala.concurrent.{Future, ExecutionContext}

implicit def futureFunctor
	(implicit ec: ExecutionContext): Functor[Future] =
	new Functor[Future] {
		def map[A, B](value: Future[A])(func: A => B): Future[B] =
      			value.map(func)
  }
```

[Scala ExecutionContextって何 / Futureはスレッド立ち上げじゃないよ](https://mashi.hatenablog.com/entry/2014/11/24/010417)

[Future や ExecutionContext をなんとなく触ってる人のために](https://qiita.com/takat0-h0rikosh1/items/b42cd4dd4ca0fc6770fa)



Future の場合の例の説明がよくわからない

- Some􏰀mes we need to inject dependencies into our instances
- Functor for Future, we would need to account for the implicit ExecutionContext parameter on future.map
- We can’t add extra parameters to functor.map so we have to account for the dependency when we create the instance

```
import scala.concurrent.{Future, ExecutionContext}

implicit def futureFunctor
(implicit ec: ExecutionContext): Functor[Future] =
new Functor[Future] {
def map[A, B](value: Future[A])(func: A => B): Future[B] =
      value.map(func)
  }
```


```
// We write this:
Functor[Future]

// The compiler expands to this first:
Functor[Future](futureFunctor)

// And then to this:
Functor[Future](futureFunctor(executionContext))
```


#### 3.5.4 演習

Tree クラスに対して Functor を実装する

```
import cats.Functor

sealed trait Tree[+A]

final case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]

final case class Leaf[A](value: A) extends Tree[A]



// 回答

implicit val treeFunctor: Functor[Tree] =
  new Functor[Tree] {
    def map[A, B](tree: Tree[A])(func: A => B): Tree[B] =
    // Tree 継承しているクラスのオブジェクトに対してまとめて functor を定義する
    // mach を用いて，クラスを判別することで，それぞれに対する map 処理を定義すれば良い
      tree match {
        case Branch(left, right) =>
          // Branch の場合は left, right に持つ Branch に再帰的に map 処理を適用
          // 再帰的に適用し続け，最終的に Leaf に到達すれば走査が完了する
          Branch(map(left)(func), map(right)(func))
        case Leaf(value) =>
          // Leaf の場合は value に対して map処理を適用
          Leaf(func(value))
      }
  }


Branch(Leaf(10), Leaf(20)).map(_*2)

// <console>:22: error: value map is not a member of Branch[Int]
//   Branch(Leaf(10), Leaf(20)).map(_*2)

// scala は Tree に対するFunctor インスタンスを見つけることはできるが
// Tree を継承している Branch, Leaf に対しては見つけることが出来ない
// なので Tree 以下のように Branch, Leaf のコンストラクタを定義してあげる必要がある

object Tree {
  def branch[A](left: Tree[A], right: Tree[A]): Tree[A] = Branch(left, right)
  def leaf[A](value: A): Tree[A] = Leaf(value)
}

Tree.branch(Tree.leaf(10), Tree.leaf(20)).map(_ * 5)
```


- 参考
	- [第21章：Scalaの型パラメータ](https://qiita.com/f81@github/items/7a664df8f4b87d86e5d8)
		- 変位指定アノテーション
			- 共変　List[+A]
				- 型AとAを継承するクラスのListに限定
			- 反変　List[-A]
				- 型AとAの親クラスのListに限定
			- 非変　List[A]
				- 型AのListに限定

[Multiple type parameters in type class](https://stackoverflow.com/questions/37981356/multiple-type-parameters-in-type-class)



### 3.6 Contravariant and Invariant Functors

#### 3.6.1 Contravariant Functors and the contramap Method

- contramap
	- map とは逆方向の処理を行える
		- 元処理：A -> B
		- contramap に与える処理：C -> A
		- contramap の結果として得られる処理：C -> B
	- map の場合
		- 元処理：A -> B
		- contramap に与える処理：B -> C
		- contramap の結果として得られる処理：A -> C
- 何が嬉しい？
	- 既存の 型クラスのインスタンスから別の型クラスインスタンスを簡単に作成できる
		- 型の変換を行える関数さえあればよい
	- 例えば Printable[String] がすでにインスタンスとして存在するときに Printable[Box[String]]  を作るのが contramap を使えば簡単にできる
		- Box[String] => String となる関数が定義されている必要がある



#### 3.6.2 Invariant functors and the imap method

- imap
	- 両方向の型変換を持つ処理に対して，それら２つの処理の型を変換できる
	- imap の処理
		- 元処理２つ(両方向の型変換が定義されている）
			- A -> X
			- X -> A
		- imap に与える処理２つ
			- A -> B
			- B -> A
		- imap の結果として得られる処理２つ
			- B -> X   (B -> A -> X)
			- X -> B   ( X -> A  -> B)


### 3.7

上記２つを cats を用いて利用
