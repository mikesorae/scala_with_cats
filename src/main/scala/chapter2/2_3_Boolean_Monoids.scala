trait Semigroup[A] {
  def combine(x: A, y: A): A
}

trait Monoid[A] extends Semigroup[A] {
  def empty: A
}

//object Monoid {
//  def apply[A](implicit monoid: Monoid[A]) =
//    monoid
//}

object Monoid {
  def apply[A](implicit monoid: Monoid[A]) =
    monoid
}


// Boolean のモノイドとなりうる処理を考える
// Boolean は true, false の２値
// ???
// empty を true, false のどちらかで 定義できる処理とは？？
// combine(true, empty) = true, combine(false, empty) = false
// combine(empty, true) = true, combine(empty, false) = false

// Boolean 同士の演算は定義されていないので これらを条件にした Boolean 生成処理？？を考えるのか？

// Boolean 変換ルール
// return a || b ー> associative な処理 かつ，false がempty か
// return a && b ー> associative な処理 かつ，true がempty か

// 例
//implicit val booleanAndMonoid: Monoid[Boolean] =
//  new Monoid[Boolean] {
//    def combine(a: Boolean, b: Boolean) = a && b
//    def empty = true
//  }



// set のモノイドとなりうる処理を考える
// set は重複要素の存在しない集合. 集合演算が定義されている ?? かな？？
// 和，差，積，XOR，空集合
// ー＞ 和 (or)：associative, empty：空集合
// ー＞ 差：associativeではない A.sub(B) と B.sun(A) は違う
// ー＞ 積 (and)：associative, empty：存在しないー＞ モノイドではない
// ー＞ 積の反対 (!and) ：associativeか, empty：空集合

