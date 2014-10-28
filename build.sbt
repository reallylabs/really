//**
//* Copyright (C) 2014-2015 Really Inc. <http://really.io>
//**
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

version in Global := "0.1-SNAPSHOT"

scalaVersion in Global := "2.11.2"

scalacOptions in Global ++= Seq("-feature")

fork in test in Global := true

javaOptions in test in Global += "-Xmx2G"


lazy val `really-core` = project in file("really-core") settings (CoreBuild.settings: _*) configs (MultiJvm)

lazy val `really-io` = project in file("really-io") settings (IOBuild.settings: _*) enablePlugins(PlayScala) dependsOn `really-core`

