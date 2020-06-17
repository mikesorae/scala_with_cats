scalaVersion := "2.12.9"
libraryDependencies +=
  "org.typelevel" %% "cats-core" % "2.0.0"
scalacOptions ++= Seq(
//  "-Xfatal-warnings",
  "-Ypartial-unification"
)