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
package com.nominum.build;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import play.twirl.compiler.TemplateCompilationError;
import scala.collection.JavaConversions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles scala.html files to scala source files and compiles routes.
 *
 * @requiresDependencyResolution compile
 */
@Mojo(name="compile-templates",defaultPhase=LifecyclePhase.GENERATE_SOURCES)
public class TemplateCompilerMojo extends AbstractMojo {

    @Parameter(defaultValue="${project}",required=true,readonly=true)
    private MavenProject project;

    /**
     * Location of the compiled templates.
     */
    @Parameter(defaultValue="${project.build.directory}/generated-sources/play-templates",required=true)
    private File generatedSourcesDirectory;

    /**
     * Location of the source files.
     */
    @Parameter(defaultValue="${project.build.sourceDirectory}",required=true)
    private File sourceDirectory;

    /**
     * Location of the play conf directory.
     */
    @Parameter(defaultValue="${project.basedir}/conf",required=true)
    private File confDirectory;

    /**
     * whether reverse routes are generated
     */
    @Parameter(defaultValue="true", required=true)
    private Boolean generateReverseRouter;

    /**
     * whether templates are compiled with support for Java projects or only Scala.
     *
     * If you set this to "true", you must add the play-java dependency to your project.
     */
    @Parameter(defaultValue="true", required=false)
    private Boolean forJava;

    public void execute()
        throws MojoExecutionException {
        try {
            compileTemplatesAndRoutes(
                    absolutePath(confDirectory),
                    absolutePath(generatedSourcesDirectory),
                    project,
                    absolutePath(sourceDirectory),
                    generateReverseRouter,
                    forJava);
        } catch (TemplateCompilationError e) {
            String msg = String.format("Error in template %s:%s %s", e.source().getPath(), e.line(), e.message());
            throw new MojoExecutionException(msg);
        }
    }

    /** This static method is usable by other Mojos */
    public static void compileTemplatesAndRoutes(File confDirectory,
                                                 File outputDir,
                                                 MavenProject project,
                                                 File sourceDir,
                                                 boolean generateReverseRouter,
                                                 boolean forJava) throws MojoExecutionException {
        project.addCompileSourceRoot(outputDir.getAbsolutePath());

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) throw new MojoExecutionException("Failed to create output directory");
        }

        PlayRoutesCompiler routesCompiler = new PlayRoutesCompiler();
        routesCompiler.compile(confDirectory, outputDir,
                new scala.collection.mutable.ArrayBuffer<String>(), generateReverseRouter);

        List<File> classpathFiles = new ArrayList<File>();
        String classpath = System.getProperty("java.class.path");
        for (String path : classpath.split(":")) {
            classpathFiles.add(new File(path));
        }

        TemplateCompiler templateCompiler =
                new TemplateCompiler(JavaConversions.asScalaBuffer(classpathFiles).toList(), forJava);
        templateCompiler.compile(sourceDir, outputDir);

    }

    /** 
     * Convert Files with relative paths to be relative from the project basedir. 
     */
    private File absolutePath(File file) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(project.getBasedir(), file.getPath());
    }
}
