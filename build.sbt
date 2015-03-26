//**
//* Copyright (C) 2014-2015 Really Inc. <http://really.io>
//**
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

version in Global := "0.1-SNAPSHOT"

scalaVersion in Global := "2.11.6"

scalacOptions in Global ++= Seq("-feature", "-deprecation")

fork in test in Global := true

//mainClass in really in Compile := (mainClass in `really-io` in Compile).value
//
//fullClasspath in really in Runtime ++= (fullClasspath in `really-io` in Runtime).value

lazy val `really-utils` = project in file("modules/really-utils") settings (UtilsBuild.settings: _*) settings (scalariformSettings: _*)

lazy val `really-core` = project in file("modules/really-core") settings (CoreBuild.settings: _*) settings (scalariformSettings: _*) configs (MultiJvm) dependsOn `really-utils`

lazy val `really-simple-auth` = project in file("modules/really-simple-auth") settings (AuthBuild.settings: _*) settings (scalariformSettings: _*) enablePlugins(PlayScala) dependsOn `really-utils`

lazy val `really-docs` = project in file("really-docs") settings (DocsBuild.settings: _*)

// lazy val really = project in file("really-io") settings (IOBuild.settings: _*) settings (scalariformSettings: _*) enablePlugins(PlayScala) dependsOn(`really-core`) dependsOn( `really-simple-auth`) aggregate (`really-simple-auth`)
lazy val really = project in file(".") dependsOn(`really-core`, `really-simple-auth`) aggregate(`really-utils`, `really-core`, `really-simple-auth`, `really-docs`) enablePlugins(PlayScala) settings (IOBuild.settings: _*) settings (scalariformSettings: _*)
