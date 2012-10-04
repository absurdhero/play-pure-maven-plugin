package com.nominum.play

import play.core._
import java.io.File
import play.api.{Play, Mode, Application}

/**
 * Creates and initializes an Application in development mode.
 * @param applicationPath location of an Application
 */
class StaticDevApplication(applicationPath: File) extends ApplicationProvider {

  val application = new Application(applicationPath, this.getClass.getClassLoader, None, Mode.Dev)

  Play.start(application)

  def get = Right(application)
  def path = applicationPath
}
