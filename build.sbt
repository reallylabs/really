import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

scalaVersion := "2.11.2"

scalacOptions in Global ++= Seq("-feature")

fork in test in Global := true

javaOptions in test in Global += "-Xmx2G"


lazy val `really-core` = project in file("really-core") settings (CoreBuild.settings: _*) configs (MultiJvm)

lazy val `really-io` = project in file("really-io") settings (IOBuild.settings: _*) dependsOn `really-core`

