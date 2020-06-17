import cats.Show
import cats.implicits._

//case class Cat(name: String, age: Int, color: String)

object ShowInstances {
  implicit val catShow: Show[Cat] =
    new Show[Cat] {
      def show(value: Cat): String = s"${value.name} is a ${value.age} year-old ${value.color} cat."
    }
}
