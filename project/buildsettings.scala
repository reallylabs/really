/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

import com.typesafe.sbt.SbtScalariform._
import sbt._
import Keys._

object BuildSettings {
  val default = Seq(
    organization := s"io.really",
    homepage := Some(url("https://github.com/cloud9ers/really")),
    testOptions in Test += Tests.Argument("-oD"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
      "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
    )
  ) ++ FormatSettings.settings
}
