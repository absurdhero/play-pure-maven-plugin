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
package com.nominum.build

import java.io.File
import play.routes.compiler._
import com.nominum.build.Util.filesInDirStartingWith
import org.apache.maven.plugin.MojoExecutionException

/** The routes compiler generates static routes and optionally reverse routes.
  *
  * It does not currently support injected routes as found in Play 2.4.
  */
class PlayRoutesCompiler {
  def compile(confDirectory: File, generatedDir: File, additionalImports: Seq[String], generateReverseRouter: Boolean) = {

    var routesFile = new File(confDirectory, "routes")
    if (!routesFile.exists) {
       for (file <- confDirectory.listFiles.filter(_.getName.endsWith("routes"))) {
         if (file.exists) routesFile = file
       }
    }
    val routerTask = new RoutesCompiler.RoutesCompilerTask(routesFile, additionalImports, true, generateReverseRouter, false)

    RoutesCompiler.compile(routerTask, StaticRoutesGenerator, generatedDir).fold(
      (errors: Seq[RoutesCompilationError]) =>
        throw new MojoExecutionException(errors.head, "Error in routes file on line " + errors.head.line, errors.head.message),
      (sf: Seq[File]) => {}
    )
    (filesInDirStartingWith(generatedDir, "routes_*") ++ Array(new File(generatedDir, "routes.java"))).map(_.getAbsoluteFile)
  }
}
