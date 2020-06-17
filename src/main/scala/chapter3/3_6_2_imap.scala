// ２方向の変換を渡してあげる事で，既存の encode, decode の対応する型を変更させられると
// contramap は　1方向の変換用いて既存の１方向の変換が対応する型を変更していた

trait Codec[A] {
  def encode(value: A): String
  def decode(value: String): A
  def imap[B](dec: A => B, enc: B => A): Codec[B] =
    new Codec[B] {
      def encode(value: B): String =
        Codec.this.encode(enc(value))

      def decode(value: String): B =
        dec(Codec.this.decode(value))
    }
}

def encode[A](value: A)(implicit c: Codec[A]): String = c.encode(value
def decode[A](value: String)(implicit c: Codec[A]): A = c.decode(value)


implicit val stringCodec: Codec[String] =
  new Codec[String] {
    def encode(value: String): String = value
    def decode(value: String): String = value
  }

implicit val intCodec: Codec[Int] =
  stringCodec.imap(_.toInt, _.toString)

implicit val booleanCodec: Codec[Boolean] =
  stringCodec.imap(_.toBoolean, _.toString)


// Codec[String] から Codec[Double] を作成する
// encode, decode を定義しなければ行けない関係で任意の Codec[A] から特定の Codec は生成できないのでは？？
// 任意の型から　Double へ変換する enc, 逆の dec が必要になるため
implicit def doubleCodec(implicit p: Codec[String]): Codec[Double] =
  p.imap[Double](_.toDouble, _.toString)


// implement codec for the following Box type
case class Box[A](value: A)

// Codec[Box[A]] を作成するので， encode, decode を実装する必要がある
// or Codec[String] から作成？？ ただ A が String とは限らないので
// いや違うか Codec[Box[A]] は Codec[String] から作成できる
// String => Box[A] ，逆のメソッドを提供すればよいだけ
// A._toString は良いとして A に戻すのはどうやって？ 汎用的にできる？？

// なるほど 作成元を Codec[A] とする & Codec[A] の実装はすでにあるとして実装すれば
// String => A, A => String とする方法は Codec[A] にまかせて　Codec[Box[A]] を作れると


implicit def boxCodec[A](implicit c: Codec[A]): Codec[Box[A]] =
  c.imap[Box[A]](Box(_), _.value)


/**
encode(123.4)
// res10: String = "123.4"

// decode は 引数から用いるべき型を特定できないので，関数呼び出し時に明示的に型パラメータを渡す必要がある
decode[Double]("123.4")
// res11: Double = 123.4

encode(Box(123.4))
// res12: String = "123.4"

decode[Box[Double]]("123.4")
// res13: Box[Double] = Box(123.4)

**/