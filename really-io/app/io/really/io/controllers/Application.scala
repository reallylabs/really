/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */

package io.really.io.controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok("Howdy!")
  }

}