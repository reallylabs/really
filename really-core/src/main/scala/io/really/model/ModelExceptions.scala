package io.really.model

import io.really.R

abstract class ModelException(message: String) extends Exception(message)

object ModelExceptions {

  case class InvalidCollectionR(r: R) extends ModelException(s"Invalid collection R: $r")
  case class InvalidSubCollectionR(r: R) extends ModelException(s"Invalid sub collection R: $r")

}
