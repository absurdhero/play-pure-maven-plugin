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
import play.router._
import play.router.RoutesCompiler._
import com.nominum.build.Util.filesInDirStartingWith
import org.apache.maven.plugin.MojoExecutionException

class PlayRoutesCompiler {
  def compile(confDirectory: File, generatedDir: File, additionalImports: Seq[String]) = {

    (Array(new File(generatedDir, "routes.java")) ++ filesInDirStartingWith(generatedDir, "routes_*")).filter(_ == null).map(GeneratedSource(_)).foreach(_.sync())
      var routesFile = new File(confDirectory, "routes")
      if (!routesFile.exists) {
         for (file <- confDirectory.listFiles.filter(_.getName.endsWith("routes"))) {
           if (file.exists) routesFile = file
         }
      }
      try {
        RoutesCompiler.compile(routesFile, generatedDir, additionalImports)
      } catch {
        case e: RoutesCompilationError => throw new MojoExecutionException("Error in routes file on line " + e.line, e)
      }
      (filesInDirStartingWith(generatedDir, "routes_*") ++ Array(new File(generatedDir, "routes.java"))).map(_.getAbsoluteFile)
  }

  
}
