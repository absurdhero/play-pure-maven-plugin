package com.nominum.build

/*
 * Copyright 2012 Nominum, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File

import com.nominum.build.Util.filesInDirEndingWith

import scala.io.Codec

class TemplateCompiler(classpath: Seq[File], forJava: Boolean) {
  // from the play sbt-plugin
  val templatesImport : Seq[String] =
  if (forJava) {
    Seq(
      "models._",
      "controllers._",
      "java.lang._",
      "java.util._",
      "scala.collection.JavaConversions._",
      "scala.collection.JavaConverters._",
      "play.api.i18n._",
      "play.core.j.PlayMagicForJava._",
      "play.mvc._",
      "play.data._",
      "play.api.data.Field",
      "play.mvc.Http.Context.Implicit._",
      "views.html._" )

  } else {
    Seq(
      "models._",
      "controllers._",
      "play.api.i18n._",
      "play.api.mvc._",
      "play.api.data._",
      "views.html._")
  }

  val fileExtensions = Map(
    "html" -> "play.twirl.api.HtmlFormat",
    "js"  -> "play.twirl.api.JavaScriptFormat",
    "txt" -> "play.twirl.api.TxtFormat",
    "xml" -> "play.twirl.api.XmlFormat"
  )
  play.twirl.api.Formats

  def compile(sourceDirectory: File, generatedDir: File) = {
    val classLoader = new java.net.URLClassLoader(classpath.map(_.toURI.toURL).toArray, this.getClass.getClassLoader)
    val compiler = classLoader.loadClass("play.twirl.compiler.TwirlCompiler")
    val generatedSource = classLoader.loadClass("play.twirl.compiler.GeneratedSource")

    // remove scala source files that no longer correspond with an html template file
    filesInDirEndingWith(generatedDir, ".template.scala").foreach {
      source =>
        val constructor = generatedSource.getDeclaredConstructor(classOf[File], classOf[Codec])
        val sync = generatedSource.getDeclaredMethod("sync")
        val defaultCodec: AnyRef = generatedSource.getDeclaredMethod("apply$default$2").invoke(null)
        val generated =  constructor.newInstance(source, defaultCodec)
        try {
          sync.invoke(generated)
        } catch {
          case e: java.lang.reflect.InvocationTargetException => {
            val t = e.getTargetException
            t.printStackTrace()
            throw t
          }
        }
    }

    // generate scala sources from each supported file extension
    fileExtensions.foreach {
      case (ext: String, formatter: String) => {

        filesInDirEndingWith(sourceDirectory, ".scala." + ext).foreach {
          template =>
            try {

              val compile = compiler.getDeclaredMethod("compile", classOf[java.io.File], classOf[java.io.File], classOf[java.io.File], classOf[String], classOf[String], classOf[Codec], classOf[Boolean], classOf[Boolean])
              val defaultCodec = compiler.getDeclaredMethod("compile$default$6").invoke(null)
              val defaultForInclusiveDot = compiler.getDeclaredMethod("compile$default$7").invoke(null)
              val defaultForUseOldParser = compiler.getDeclaredMethod("compile$default$8").invoke(null)
              compile.invoke(null, template, sourceDirectory, generatedDir, formatter, "import play.twirl.api._\nimport play.twirl.api.TemplateMagic._" + "\nimport " + templatesImport.mkString("\nimport "), defaultCodec, defaultForInclusiveDot, defaultForUseOldParser)

            } catch {
              case e: java.lang.reflect.InvocationTargetException => {
                throw e.getTargetException
              }
            }
        }
      }
    }

    filesInDirEndingWith(generatedDir, ".scala").map(_.getAbsoluteFile)
  }

}
