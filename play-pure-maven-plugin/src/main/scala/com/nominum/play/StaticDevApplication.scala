package com.nominum.play

import play.core._
import java.io.File
import play.api.{Play, Mode, DefaultApplication}

/**
 * Creates and initializes an Application in development mode.
 * @param applicationPath location of an Application
 */
class StaticDevApplication(applicationPath: File) extends ApplicationProvider {

  val application = new DefaultApplication(applicationPath, this.getClass.getClassLoader, None, Mode.Dev)

  Play.start(application)

  def get = Right(application)
  def path = applicationPath
}
