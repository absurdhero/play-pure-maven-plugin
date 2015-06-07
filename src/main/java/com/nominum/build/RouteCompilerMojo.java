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

import java.io.File;

/**
 * Compiles routes.
 *
 * @requiresDependencyResolution compile
 */
@Mojo(name="compile-routes",defaultPhase=LifecyclePhase.GENERATE_SOURCES)
public class RouteCompilerMojo extends AbstractMojo {

    @Parameter(defaultValue="${project}",required=true,readonly=true)
    private MavenProject project;

    /**
     * Location of the source files.
     */
    @Parameter(defaultValue="${project.build.sourceDirectory}",required=true)
    private File sourceDirectory;

    /**
     * Location of the compiled routes.
     */
    @Parameter(defaultValue="${project.build.directory}/generated-sources/play-templates",required=true)
    private File generatedSourcesDirectory;

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
            compileRoutes(
                    absolutePath(confDirectory),
                    absolutePath(generatedSourcesDirectory),
                    project,
                    generateReverseRouter);
        } catch (TemplateCompilationError e) {
            String msg = String.format("Error in template %s:%s %s", e.source().getPath(), e.line(), e.message());
            throw new MojoExecutionException(msg);
        }
    }

    /** This static method is usable by other Mojos */
    public static void compileRoutes(File confDirectory,
                                     File outputDir,
                                     MavenProject project,
                                     boolean generateReverseRouter)
            throws MojoExecutionException {
        project.addCompileSourceRoot(outputDir.getAbsolutePath());

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) throw new MojoExecutionException("Failed to create output directory");
        }

        PlayRoutesCompiler routesCompiler = new PlayRoutesCompiler();
        routesCompiler.compile(confDirectory, outputDir,
                new scala.collection.mutable.ArrayBuffer<String>(), generateReverseRouter);
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
