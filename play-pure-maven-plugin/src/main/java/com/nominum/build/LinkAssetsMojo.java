package com.nominum.build;

/*
 * Copyright 2012 Nominum, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Link static assets directory into build output directory.
 * 
 * This Mojo only works on Unix-like systems with the "ln" program.
 * 
 * @goal link-assets
 * 
 * @phase generate-sources
 */
public class LinkAssetsMojo extends AbstractMojo {

	/**
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * Output directory in classpath.
	 * 
	 * @parameter expression="${project.build.outputDirectory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * Location of the assets directory.
	 * 
	 * @parameter expression="${project.basedir}/public"
	 * @required
	 */
	private File assetDirectory;

	public void execute() throws MojoExecutionException {

		File outputDir = absolutePath(outputDirectory);
		File assetDir = absolutePath(assetDirectory);

		if (!assetDir.exists()) {
			getLog().info("assets directory not found, no assets to copy");
			return;
		}

		if (!outputDir.exists()) {
			boolean created = outputDir.mkdirs();
			if (!created)
				throw new MojoExecutionException("Failed to create output directory");
		}

		String linkName = assetDir.getAbsolutePath().substring(assetDir.getParent().length() + 1);
		File copyTarget = new File(outputDir, linkName);

		// recreate link if it exists
		if (copyTarget.exists()) {
			boolean deleted = copyTarget.delete();
			if (!deleted) {
				throw new MojoExecutionException("Failed to delete " + linkName + " prior to linking asset directory");
			}
		}

		try {
			getLog().info("Copying assets " + assetDirectory + " to " + copyTarget);
			FileUtils.copyDirectory(assetDirectory, copyTarget);
		} catch (IOException e) {
			throw new MojoExecutionException("Copying assets directory failed. ",e);
		}

		getLog().info("Scanning for submodules: ");
		String[] directories = new File(project.getBasedir(), "modules").list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return new File(dir, name).isDirectory();
			}
		});
		getLog().info("found: " + Arrays.toString(directories));
		if (directories != null) for (String module : directories) {
			File moduleDir = absolutePath(new File(project.getBasedir(), module));
			try {
				FileUtils.copyDirectory(new File(moduleDir, "public"), assetDir);
			} catch (IOException e) {
				getLog().error("Cannot copy assets from module "+ module +" to "+ assetDir.getAbsolutePath(),e);
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
}
