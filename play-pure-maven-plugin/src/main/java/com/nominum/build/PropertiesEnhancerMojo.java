package com.nominum.build;

/*
 * Copyright 2013 Guido Grazioli <guido.grazioli@gmail.com>
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

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
	 * 
	 * @parameter expression="${project.build.outputDirectory}"
	 * @required
	 */
	private File outputDirectory;

	/*
	 * @parameter default-value="${plugin.artifacts}"
	 * @readonly
	 * @since 1.1-beta-1
	 */
	//private List pluginDependencies;

	
	/**
	 * A list of system properties to be passed. Note: as the execution is not
	 * forked, some system properties required by the JVM cannot be passed here.
	 * Use MAVEN_OPTS or the exec:exec instead. See the user guide for more
	 * information.
	 * 
	 * @parameter
	 * @since 1.0
	 */
	private Property[] systemProperties;

	private Properties originalSystemProperties;

	/**
	 * Defines the scope of the classpath passed to the plugin. Set to
	 * compile,test,runtime or system depending on your needs. Since 1.1.2, the
	 * default value is 'runtime' instead of 'compile'.
	 * 
	 * @parameter expression="${exec.classpathScope}" default-value="runtime"
	 */
	protected String classpathScope;

	/**
	 * Indicates if the project dependencies should be used when executing the
	 * main class.
	 * 
	 * @parameter expression="${exec.includeProjectDependencies}"
	 *            default-value="true"
	 * @since 1.1-beta-1
	 */
	private boolean includeProjectDependencies;

	public void execute() throws MojoExecutionException {
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(getClassLoader());
		setSystemProperties();
		StringBuffer buffer = new StringBuffer();
		for (URL url : ((URLClassLoader) (Thread.currentThread().getContextClassLoader())).getURLs()) {
		  buffer.append(new File(url.getPath()));
		  buffer.append(System.getProperty("path.separator"));
		}
		String classpath = buffer.toString();
		classpath = outputDirectory.getAbsolutePath() + System.getProperty("path.separator") + classpath;
		getLog().info("classpath: " + classpath);
		enhanceProperties(project, absolutePath(outputDirectory), classpath);
		if (originalSystemProperties != null) {
			System.setProperties( originalSystemProperties );
		}
		Thread.currentThread().setContextClassLoader(originalClassLoader);
	}

	/** This static method is usable by other Mojos */
	public void enhanceProperties(MavenProject project, File targetDir, String classpath) throws MojoExecutionException {

		// get all the files from a directory
		File[] fList = targetDir.listFiles();
		getLog().info("Directory: " + targetDir.getAbsolutePath());
		for (File classFile : fList) {
			if (classFile.isFile() && classFile.getName().endsWith(".class")) {
				getLog().info("Processing " + classFile.getName());
				try {
					play.core.enhancers.PropertiesEnhancer.generateAccessors(classpath, classFile);
					play.core.enhancers.PropertiesEnhancer.rewriteAccess(classpath, classFile);
				} catch (Exception e) {
					throw new MojoExecutionException("Error while enhancing properties on "
							+ classFile.getAbsolutePath(), e);
				}
			} else if (classFile.isDirectory()) {
				enhanceProperties(project, classFile, classpath);
			}
		}
	}

	/**
	 * Convert Files with relative paths to be relative from the project
	 * basedir.
	 **/
	private File absolutePath(File file) {
		if (file.isAbsolute()) {
			return file;
		}
		return new File(project.getBasedir(), file.getPath());
	}

	/**
	 * Add any relevant project dependencies to the classpath. Takes
	 * includeProjectDependencies into consideration.
	 * 
	 * @param path
	 *            classpath of {@link java.net.URL} objects
	 * @throws MojoExecutionException
	 *             if a problem happens
	 */
	private void addRelevantProjectDependenciesToClasspath(List<URL> path) throws MojoExecutionException {
		if (this.includeProjectDependencies) {
			try {
				getLog().debug("Project Dependencies will be included.");

				List<Artifact> artifacts = new ArrayList<Artifact>();
				List<File> theClasspathFiles = new ArrayList<File>();

				collectProjectArtifactsAndClasspath(artifacts, theClasspathFiles);

				for (Iterator<File> it = theClasspathFiles.iterator(); it.hasNext();) {
					URL url = it.next().toURI().toURL();
					getLog().debug("Adding to classpath : " + url);
					path.add(url);
				}

				Iterator<Artifact> iter = artifacts.iterator();
				while (iter.hasNext()) {
					Artifact classPathElement = iter.next();
					getLog().debug("Adding project dependency artifact: " + classPathElement.getArtifactId() + " to classpath");
					path.add(classPathElement.getFile().toURI().toURL());
				}

			} catch (MalformedURLException e) {
				throw new MojoExecutionException("Error during setting up classpath", e);
			}
		} else {
			getLog().debug("Project Dependencies will be excluded.");
		}

	}

	/**
	 * Pass any given system properties to the java system properties.
	 */
	private void setSystemProperties() {
		if (systemProperties != null) {
			originalSystemProperties = System.getProperties();
			for (int i = 0; i < systemProperties.length; i++) {
				Property systemProperty = systemProperties[i];
				String value = systemProperty.getValue();
				System.setProperty(systemProperty.getKey(), value == null ? "" : value);
			}
		}
	}

	/**
	 * Set up a classloader for the execution of the main class.
	 * 
	 * @return the classloader
	 * @throws MojoExecutionException
	 *             if a problem happens
	 */
	private ClassLoader getClassLoader() throws MojoExecutionException {
		List<URL> classpathURLs = new ArrayList<URL>();
		// this.addRelevantPluginDependenciesToClasspath( classpathURLs );
		this.addRelevantProjectDependenciesToClasspath(classpathURLs);
		return new URLClassLoader((URL[]) classpathURLs.toArray(new URL[classpathURLs.size()]));
	}

	/**
	 * Collects the project artifacts in the specified List and the project
	 * specific classpath (build output and build test output) Files in the
	 * specified List, depending on the plugin classpathScope value.
	 * 
	 * @param artifacts
	 *            the list where to collect the scope specific artifacts
	 * @param theClasspathFiles
	 *            the list where to collect the scope specific output
	 *            directories
	 */
	@SuppressWarnings("unchecked")
	protected void collectProjectArtifactsAndClasspath(List<Artifact> artifacts, List<File> theClasspathFiles) {

		if ("compile".equals(classpathScope)) {
			artifacts.addAll(project.getCompileArtifacts());
			theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
		} else if ("test".equals(classpathScope)) {
			artifacts.addAll(project.getTestArtifacts());
			theClasspathFiles.add(new File(project.getBuild().getTestOutputDirectory()));
			theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
		} else if ("runtime".equals(classpathScope)) {
			artifacts.addAll(project.getRuntimeArtifacts());
			theClasspathFiles.add(new File(project.getBuild().getOutputDirectory()));
		} else if ("system".equals(classpathScope)) {
			artifacts.addAll(project.getSystemArtifacts());
		} else {
			throw new IllegalStateException("Invalid classpath scope: " + classpathScope);
		}

		getLog().debug("Collected project artifacts " + artifacts);
		getLog().debug("Collected project classpath " + theClasspathFiles);
	}
}
