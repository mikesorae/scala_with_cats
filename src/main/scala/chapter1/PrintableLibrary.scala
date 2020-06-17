
// 型クラスを定義
trait Printable[A] {
  def format(value: A): String
}

object PrintableInstances {
  implicit val stringPrinter: Printable[String] =
    new Printable[String] {
      def format(value: String): String = value
    }

  implicit val intPrinter: Printable[Int] =
    new Printable[Int] {
      def format(value: Int): String = value.toString
    }

  implicit val catPrinter: Printable[Cat] =
    new Printable[Cat] {
      def format(value: Cat): String = s"${value.name} is a ${value.age} year-old ${value.color} cat."
    }
}


/*
 Define an object Printable with two generic interface methods:
format accepts a value of type A and a Printable of the correspond- ing type. It uses the relevant Printable to convert the A to a String.
 */
// Interface Objects で作成
object Printer {
  def format[A](value: A)(implicit p: Printable[A]): String = p.format(value)
  def print[A](value: A)(implicit p: Printable[A]): Unit = println(format(value))
    // なるほど，自分の format メソッドを用いて文字列に変換してから printlnすればよいのか
    // このメソッドに implicit な引数が必要なのはなぜ？？ format には必要だが
}

// Interface Syntax で作成
object PrintableSyntax {
  implicit class PrintableOps[A](value: A) {
    def format(implicit p: Printable[A]): String = p.format(value)
    def print(implicit p: Printable[A]): Unit = println(format(p))
  }
}


// Cat クラスを作成し，Catクラスに対する Printable を実装する
case class Cat(name: String, age: Int, color: String)


/*
// sbt console での 実行例
import PrintableInstances._
import PrintableSyntax._

// Interface Syntax を利用
val cat = Cat("ash", 28, "yellow")
cat.format // -＞　res1: String = ash is a 28 year-old yellow cat.

// Interface Object を使用
Printer.fromat(cat) // -> res4: String = ash is a 28 year-old yellow cat.
 */