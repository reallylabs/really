scalaVersion := "2.11.2"

scalacOptions in Global ++= Seq("-feature")

lazy val `really-core` = project in file("really-core") settings (CoreBuild.settings: _*)

lazy val `really-io` = project in file("really-io") settings (IOBuild.settings: _*) dependsOn `really-core`
