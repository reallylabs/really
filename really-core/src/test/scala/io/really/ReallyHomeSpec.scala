package io.really

import java.nio.file.attribute.PosixFilePermissions

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ FlatSpec, Matchers }
import java.nio.file._

import scala.util.Random

class ReallyHomeSpec extends FlatSpec with Matchers {

  val parentConfig = ConfigFactory.load("really-core-test")
  "ReallyHome" should "be specified by really.home key" in {
    val config = ConfigFactory.parseString("really.home = /tmp")
    val conf: ReallyConfig = new ReallyConfig(config.withFallback(parentConfig))
    val path = FileSystems.getDefault.getPath("/tmp")
    assert(conf.getReallyHome === path.toRealPath())
  }
  it should "fallback to current-working-directory/.really if not specified" in {
    val conf: ReallyConfig = new ReallyConfig(parentConfig)
    val path = FileSystems.getDefault.getPath(System.getProperty("user.dir") + FileSystems.getDefault.getSeparator + ".really")
    assert(conf.getReallyHome === path)
  }
  it should "fail if the value is not directory" in {
    val config = ConfigFactory.parseString("really.home = /etc/hosts")
    val conf: ReallyConfig = new ReallyConfig(config.withFallback(parentConfig))
    val e = intercept[ReallyConfigException] {
      conf.getReallyHome
    }
    assert(e.getMessage === s"really.home (/etc/hosts) is not a directory.")
  }
  // it should "fail if the directory is not readable" in {
  //   val dirName = Random.alphanumeric.take(10).mkString
  //   val p = FileSystems.getDefault.getPath("/tmp/" + dirName)
  //   val permissions = PosixFilePermissions.fromString("-w--w--w-")
  //   Files.createDirectory(p, PosixFilePermissions.asFileAttribute(permissions))

  //   val config = ConfigFactory.parseString("really.home = " + p.toString)
  //   val conf: ReallyConfig = new ReallyConfig(config.withFallback(parentConfig))
  //   val e = intercept[ReallyConfigException] {
  //     conf.getReallyHome
  //   }
  //   assert(e.getMessage === s"really.home ($p) is not readable (permissions).")
  //   Files.delete(p)
  // }

}