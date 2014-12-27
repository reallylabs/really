//**
//* Copyright (C) 2014-2015 Really Inc. <http://really.io>
//**
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

version in Global := "0.1-SNAPSHOT"

scalaVersion in Global := "2.11.4"

scalacOptions in Global ++= Seq("-feature", "-deprecation")

fork in test in Global := true

javaOptions in test in Global += "-Xmx2G"

lazy val `really-utils` = project in file("really-utils") settings (UtilsBuild.settings: _*) settings (scalariformSettings: _*)

lazy val `really-core` = project in file("really-core") settings (CoreBuild.settings: _*) settings (scalariformSettings: _*) configs (MultiJvm) dependsOn `really-utils`

lazy val `really-io` = project in file("really-io") settings (IOBuild.settings: _*) settings (scalariformSettings: _*) enablePlugins(PlayScala) dependsOn `really-core`

lazy val `really-simple-auth` = project in file("really-simple-auth") settings (AuthBuild.settings: _*) settings (scalariformSettings: _*) enablePlugins(PlayScala) dependsOn `really-utils`

site.settings

site.sphinxSupport()