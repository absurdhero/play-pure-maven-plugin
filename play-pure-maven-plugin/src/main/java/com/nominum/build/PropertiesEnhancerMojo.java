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

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import play.templates.TemplateCompilationError;

import com.nominum.play.PropertiesEnhancer;

/**
 * Provides property support for Java classes via byte code enchancement.  
 *
 * @goal properties-enhance
 * 
 * @phase process-classes
 *
 * @requiresDependencyResolution compile
 */
public class PropertiesEnhancerMojo extends AbstractMojo {

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * Location of the class files.
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File outputDirectory;

    public void execute() throws MojoExecutionException {
        try {
        	enhanceProperties(project, absolutePath(outputDirectory));
        } catch (TemplateCompilationError e) {
            String msg = String.format("Error in template %s:%s %s", e.source().getPath(), e.line(), e.message());
            throw new MojoExecutionException(msg);
        }
    }

    
    /** This static method is usable by other Mojos */
    public static void enhanceProperties(MavenProject project, File targetDir) throws MojoExecutionException {
    	
        String classpath = System.getProperty("java.class.path");
        
		//get all the files from a directory
        File[] fList = targetDir.listFiles();
        for (File classFile : fList){
            if (classFile.isFile() && classFile.getName().endsWith(".class")){
            	try {
					PropertiesEnhancer.generateAccessors(classpath, classFile);
					PropertiesEnhancer.rewriteAccess(classpath, classFile);
				} catch (Exception e) {
					throw new MojoExecutionException("Error while enhancing properties on "+classFile.getAbsolutePath(),e);
				}
            } else if (classFile.isDirectory()){
                enhanceProperties(project, classFile);
            }
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
