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

  def compile(sourceDirectory: File, generatedDir: File) = {
    val classLoader = new java.net.URLClassLoader(classpath.map(_.toURI.toURL).toArray, this.getClass.getClassLoader)
    val compiler = classLoader.loadClass("play.templates.ScalaTemplateCompiler")
    val generatedSource = classLoader.loadClass("play.templates.GeneratedSource")

    // remove scala source files that no longer correspond with an html template file
    filesInDirEndingWith(generatedDir, ".template.scala").foreach {
      source =>
        val constructor = generatedSource.getDeclaredConstructor(classOf[java.io.File])
        val sync = generatedSource.getDeclaredMethod("sync")
        val generated = constructor.newInstance(source)
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

    // generate scala sources from html
    filesInDirEndingWith(sourceDirectory, ".scala.html").foreach {
      template =>
        val compile = compiler.getDeclaredMethod("compile", classOf[java.io.File], classOf[java.io.File], classOf[java.io.File], classOf[String], classOf[String])
        try {
          compile.invoke(null, template, sourceDirectory, generatedDir, "play.api.templates.HtmlFormat", "import play.api.templates._\nimport play.api.templates.PlayMagic._" + "\nimport " + templatesImport.mkString("\nimport "))
        } catch {
          case e: java.lang.reflect.InvocationTargetException => {
            throw e.getTargetException
          }
        }
    }

    filesInDirEndingWith(generatedDir, ".scala").map(_.getAbsoluteFile)
  }

}
