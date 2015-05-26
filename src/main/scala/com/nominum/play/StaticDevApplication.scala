package com.nominum.play

import java.io.File

import akka.actor.ActorSystem
import play.api._
import play.api.http._
import play.api.inject.{DefaultApplicationLifecycle, Injector, NewInstanceInjector, SimpleInjector}
import play.api.libs.concurrent.ActorSystemProvider
import play.api.libs.{Crypto, CryptoConfig, CryptoConfigParser}
import play.api.mvc.EssentialFilter
import play.core._

import scala.util.Try

/**
 * Creates and initializes an Application in development mode.
 * @param applicationPath location of an Application
 */
class StaticDevApplication(applicationPath: File) extends ApplicationProvider {

  lazy val injector: Injector = new SimpleInjector(NewInstanceInjector) + router + crypto + httpConfiguration

  lazy val httpConfiguration: HttpConfiguration = HttpConfiguration.fromConfiguration(configuration)
  lazy val httpRequestHandler: HttpRequestHandler = new DefaultHttpRequestHandler(router, httpErrorHandler, httpConfiguration, httpFilters: _*)
  lazy val httpErrorHandler: HttpErrorHandler = new DefaultHttpErrorHandler(environment, configuration, sourceMapper,
    Some(router))
  lazy val httpFilters: Seq[EssentialFilter] = Nil

  lazy val applicationLifecycle: DefaultApplicationLifecycle = new DefaultApplicationLifecycle
  lazy val application: Application = new DefaultApplication(environment, applicationLifecycle, injector,
    configuration, httpRequestHandler, httpErrorHandler, actorSystem, Plugins.empty)

  lazy val actorSystem: ActorSystem = new ActorSystemProvider(environment, configuration, applicationLifecycle).get

  lazy val cryptoConfig: CryptoConfig = new CryptoConfigParser(environment, configuration).get
  lazy val crypto: Crypto = new Crypto(cryptoConfig)
  
  //  val application = new DefaultApplication(applicationPath, this.getClass.getClassLoader, None, Mode.Dev)
  val app = new DefaultApplication()

  Play.start(app)

  def get = Try(app)

  def path = applicationPath
}
