/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._


object DocsBuild {

  import Dependencies._

  val settings = BuildSettings.default ++ site.settings ++ Seq(siteDirectory <<= target / "sphinx") ++ site.sphinxSupport() 
    
}
