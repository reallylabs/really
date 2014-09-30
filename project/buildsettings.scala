import sbt._
import Keys._

object BuildSettings {
  val default = Seq(
    organization := s"io.really",
    homepage := Some(url("https://github.com/cloud9ers/really")),
    testOptions in Test += Tests.Argument("-oD"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      "Spray" at "http://repo.spray.io",
      "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
    )
  )
}
