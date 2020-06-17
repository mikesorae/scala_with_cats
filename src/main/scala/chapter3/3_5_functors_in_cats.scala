/**
package cats

// Functor は コンストラクタの引数として type constructor F[_] を受ける
// _ の部分にはどんな方も入るとする
// F[A] を与えられて初期化された Functor で map( A->B 変換処理 ) を実行すると F[A] を渡された変換処理を用いて変換する
// 具体的な　ここの要素に適用するイテレーション部分は type class instance で実装か

trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}

**/

/**

import cats.Functor
import cats.instances.list._   // for Functor
import cats.instances.option._ // for Functor

val list1 = List(1, 2, 3)
// list1: List[Int] = List(1, 2, 3)


// list1 に List型の functor を適用
// List 型についてどんな List[_] も受け入れて特定の処理をここの要素に適用する Functor が得られる
// Scala でのラムダ式の書き方ではなく，map 時にかける書き方で処理を記述すれば OK

val list2 = Functor[List].map(list1)(_ * 2)
// list2: List[Int] = List(2, 4, 6)

val option1 = Option(123)
// option1: Option[Int] = Some(123)

val option2 = Functor[Option].map(option1)(_.toString) // option2: Option[String] = Some("123")

  **/


/**

// Functor には lift というメソッドも用意されている
// 与えられた関数の引数を Functor[_] に渡した TypeConstructor A を引数に取る関数に変える
// -> lift というのはそういうことか 型から TypeConstructor 適用後の List[A] 等にしてくれると
//  converts a func􏰀on of type A => B to one that operates over a functor and has type F[A] => F[B]:

val func = (x: Int) => x + 1
// func: Int => Int = <function1>

val liftedFunc = Functor[Option].lift(func)
// liftedFunc: Option[Int] => Option[Int] = cats.Functor$$Lambda$5433
liftedFunc(Option(1))
// res1: Option[Int] = Some(2)

**/



//// Functor Syntax

/**

// We can write a method that applies an equation to a number no matter what functor context it’s in
// -> Functor のコンテキスト（TypeConstructor が List なのか Option　なのか等？）
// に関わらず，中身の Int に対して適用する処理を定義することができる

// doMath という F[Int] (任意の TypeConstructor に Int 型を与えたもの）を引数に取る 関数を定義
// implicit 引数の functor が利用され，任意の引数に対して map メソッドが定義される？？

// doMath と 任意の TypeConstructor F[_] に対して定義される 関数を定義
// F[_] が決まると F[Int], Functor[F] も決まる ー＞ 違うか，利用するシーン的には start の F[Int] が決まると F[_] が決まるのか
// start: F[Int] に map メソッドが存在しない場合， compiler が潜在的なエラーを検知して，

//  star.map( ) ー＞ new FunctorOps(start).map() に変換する

// FunctorOpsの定義は下の通りで内部で 与えられている functor を implicit に用いる

def doMath[F[_]](start: F[Int])(implicit functor: Functor[F]): F[Int] =
  start.map(n => n + 1 * 2)


implicit class FunctorOps[F[_], A](src: F[A]) {
  def map[B](func: A => B)
      (implicit functor: Functor[F]): F[B] =
    functor.map(src)(func)
}


**/


/**

// 3.5.4 Exercise: Branching out with Functors

// Write a Functor for the following binary tree data type.

// Verify that the code works as expected on instances of Branch and Leaf

import cats.Functor

sealed trait Tree[+A]

final case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]

final case class Leaf[A](value: A) extends Tree[A]


// Tree[+A] に対する functor の Type class Instance を実装

implicit val branchFunctor: Functor[Branch] =
  new Functor[Branch] {
    def map[A, B](branchA: Branch[A])(func: A => B): Branch[B] =
      new Branch[B](new Leaf[B](branchA.))
  }

implicit val leafFunctor: Functor[Leaf] =
  new Functor[Leaf] {
    def map[A, B](leafA: Leaf[A])(func: A => B): Leaf[B] =
      new Leaf[B] (func (leafA.value))
  }


val leafLeft = new Leaf[Int](1)
val leafRight = new Leaf[Int](2)
val branch = new Branch[Int](leafLeft, leafRight)

// 数値map, 文字列map を実行
Functor[Leaf].map(leafLeft)(_*10)
Functor[Leaf].map(leafLeft)(_.toString())

// Interface Syntax として利用
import cats.syntax.functor._
Leaf(10).map(_*2)



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

**/















