// Define a very simple JSON AST
sealed trait Json
final case class JsObject(get: Map[String, Json]) extends Json
final case class JsString(get: String) extends Json
final case class JsNumber(get: Double) extends Json
final case object JsNull extends Json

// The "serialize to JSON" behaviour is encoded in this trait
trait JsonWriter[A] {
  def write(value: A): Json
}


/*
Type Class Instances
 */
case class Person(name: String, email: String)

object JsonWriterInstances {
  implicit val stringWriter: JsonWriter[String] =
    new JsonWriter[String] {
      def write(value: String): Json =
        JsString(value)
    }
  implicit val personWriter: JsonWriter[Person] =
    new JsonWriter[Person] {
      def write(value: Person): Json =
        JsObject(Map(
          "name" -> JsString(value.name),
          "email" -> JsString(value.email)
        ))
    }
  // etc...
}

/*
Type Class Interfaces
 */

// Interface Objects
// Json クラスに， toJson メソッドを作成する． toJson メソッドは渡された引数の型から writer を implicit に取得して writeメソッドを実行する
object Json {
  def toJson[A](value: A)(implicit w: JsonWriter[A]): Json =
    w.write(value)
}

/* 実行例
import JsonWriterInstances._
Json.toJson(Person("Dave", "dave@example.com"))
 */

// Json インスタンスの toJson に Person が渡されると，内部でもう一つの引数 w を自動的に型解決し，適切な JsonWrite[Person] を持ってくる
// JsonWriter[Person] は JsonWriterInstances によって personWriter: JsonWriter[Person] に解決される
//ー＞　Json.toJson(Person("Dave", "dave@example.com"))(personWriter)


// Interface Syntax
object JsonSyntax {
  implicit class JsonWriterOps[A](value: A) {
    def toJson(implicit w: JsonWriter[A]): Json =
      w.write(value)
  }
}

/* 実行例
import JsonWriterInstances._
import JsonSyntax._
Person("Dave", "dave@example.com").toJson
*/


// implicit class を用いて，対応するクラスのインスタンス自体にメソッドを生やしてしまう方法
// Person("Dave", "dave@example.com").toJson
// ー＞ JsonWriterOps(new Person(Person("Dave", "dave@example.com"))).toJson
// ー＞ JsonWriterOps(new Person(Person("Dave", "dave@example.com"))).toJson(personWriter)
// -> Person("Dave", "dave@example.com").toJson(personWriter)

// Person が JsonWriterOps によってラップされるかどうかは どうやって決まるのか・・・？