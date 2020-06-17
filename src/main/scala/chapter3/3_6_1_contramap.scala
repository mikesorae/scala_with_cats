// Type Class を定義
trait Printable[A] {
  def format(value: A): String

  // Printable[A] に対して contramap(func: B => A) を呼び出すと Printable[B] が返される
  def contramap[B](func: B => A): Printable[B] =
    new Printable[B] {
      // Printable[B] は format(value: B): String を持たなくてはいけないので定義する
      def format(value: B): String =
        Printable.this.format(func(value))
    }
}


// Printable を使う関数を定義
def format[A](value: A)(implicit p: Printable[A]): String = p.format(value)


// Type Class Instance を定義

implicit val stringPrintable: Printable[String] =
  new Printable[String] {
    def format(value: String): String =
      "\"" + value + "\""
  }
implicit val booleanPrintable: Printable[Boolean] = new Printable[Boolean] {
  def format(value: Boolean): String =
    if(value) "yes" else "no"
}


/**

format("hello")

format(true)
**/


final case class Box[A](value: A)

//Rather than writing out the complete definition from scratch (new Printable[Box] etc...),
//create your instance from an existing instance using contramap.

// 新しいクラスへの Printable を既存のクラスを用いて実装する？？
// 新しい Printable[F[A]]  を Printable[A]  を用いて実装するということか
// つまり contramap に渡せるように，func: Box[A] => A が必要と


def unbox[A](box: Box[A]): A = box.value

implicit val boxPrintable: Printable[Box[String]] = stringPrintable.contramap(unbox)

/**

format(Box("hello world"))
// res5: String = "\"hello world\""
format(Box(true))
// res6: String = "yes"

**/





// 回答

implicit def boxPrintable[A](implicit p: Printable[A]) =
  new Printable[Box[A]] {
    def format(box: Box[A]): String =
      p.format(box.value)
}

// implicit val で Printable[A] があったら使ってくれるように
// implicit def で Printable[Box[A]] を返す関数があるならそれを使って Printable[Box[A]] を作ってくれるのか
// (引数が implicit であることも必要そう）
implicit def boxPrintable[A](implicit p: Printable[A]) =
  // 型クラスを用いて定義されている関数にはちゃんと型を渡して上げる必要があると
  p.contramap[Box[A]](_.value)

































