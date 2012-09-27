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
import play.core.Router.RoutesCompiler
import play.core.Router.RoutesCompiler._
import com.nominum.build.Util.filesInDirStartingWith
import org.apache.maven.plugin.MojoExecutionException

class PlayRoutesCompiler {
  def compile(confDirectory: File, generatedDir: File, additionalImports: Seq[String]) = {

    (Array(new File(generatedDir, "routes.java")) ++ filesInDirStartingWith(generatedDir, "routes_*")).filter(_ == null).map(GeneratedSource(_)).foreach(_.sync())
    val routesFile = new File(confDirectory, "routes")
    try {
      RoutesCompiler.compile(routesFile, generatedDir, additionalImports)
    } catch {
      case e: RoutesCompilationError => throw new MojoExecutionException("Error Compiling routes", e)
    }
    (filesInDirStartingWith(generatedDir, "routes_*") ++ Array(new File(generatedDir, "routes.java"))).map(_.getAbsoluteFile)

  }
}
