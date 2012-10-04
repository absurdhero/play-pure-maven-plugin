package com.nominum.build;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import scala.collection.JavaConversions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles scala.html files to scala source files and compiles routes.
 *
 * @goal compile-templates
 * 
 * @phase generate-sources
 *
 * @requiresDependencyResolution compile
 */
public class TemplateCompilerMojo
    extends AbstractMojo
{

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Location of the compiled templates.
     * @parameter expression="${project.build.directory}/generated-sources/play-templates"
     * @required
     */
    private File generatedSourcesDirectory;

    /**
     * Location of the source files.
     * @parameter expression="${pom.build.sourceDirectory}"
     * @required
     */
    private File sourceDirectory;

    /**
     * Location of the play conf directory.
     * @parameter expression="${project.basedir}/conf"
     * @required
     */
    private File confDirectory;

    public void execute()
        throws MojoExecutionException {
        compileTemplatesAndRoutes(absolutePath(confDirectory),
                absolutePath(generatedSourcesDirectory), project, absolutePath(sourceDirectory));
    }

    /** This static method is usable by other Mojos */
    public static void compileTemplatesAndRoutes(File confDirectory, File outputDir, MavenProject project, File sourceDir) throws MojoExecutionException {
        project.addCompileSourceRoot(outputDir.getAbsolutePath());

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) throw new MojoExecutionException("Failed to create output directory");
        }

        PlayRoutesCompiler routesCompiler = new PlayRoutesCompiler();
        routesCompiler.compile(confDirectory, outputDir,
                new scala.collection.mutable.ArrayBuffer<String>());

        try {
            List<File> classpathFiles = new ArrayList<File>();
            String classpath = System.getProperty("java.class.path");
            for (String path : classpath.split(":")) {
                classpathFiles.add(new File(path));
            }

            TemplateCompiler templateCompiler =
                    new TemplateCompiler(JavaConversions.asScalaBuffer(classpathFiles).toList(), true);
            templateCompiler.compile(sourceDir, outputDir);
        }
        catch (Exception e)
        {
            throw new MojoExecutionException( "Error compiling template files", e );
        }
    }

    /** Convert Files with relative paths to be relative from the project basedir. **/
    private File absolutePath(File file) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(project.getBasedir(), file.getPath());
    }
}
