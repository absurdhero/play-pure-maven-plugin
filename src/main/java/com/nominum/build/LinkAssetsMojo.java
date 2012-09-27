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

import java.io.File;
import java.io.IOException;

/**
 * Link static assets directory into build output directory
 *
 * This Mojo only works on Unix-like systems with the "ln" program.
 *
 * @goal link-assets
 *
 * @phase generate-sources
 */
public class LinkAssetsMojo
        extends AbstractMojo
{

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Output directory in classpath
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File outputDirectory;

    /**
     * Location of the play conf directory
     * @parameter expression="${project.basedir}/public"
     * @required
     */
    private File assetDirectory;

    public void execute()
            throws MojoExecutionException
    {

        File outputDir = absolutePath(outputDirectory);
        File assetDir = absolutePath(assetDirectory);

        if (!outputDir.exists())
        {
            outputDir.mkdirs();
        }

        try {
            String linkName = assetDir.getAbsolutePath().substring(assetDir.getParent().length() + 1);
            File linkTarget = new File(outputDir, linkName);

            // recreate link if it exists
            if (linkTarget.exists()) {
                linkTarget.delete();
            }

            getLog().debug("Linking " + assetDirectory + " to " + linkTarget);

            String[] command = new String[] {"ln", "-s", assetDir.getAbsolutePath(), linkTarget.getAbsolutePath()};
            Process proc = Runtime.getRuntime().exec(command, null, new File("."));
            int exitVal = proc.waitFor();
            if (exitVal != 0) {
                throw new MojoExecutionException("linking assets directory failed");
            }

        } catch (InterruptedException e) {
            throw new MojoExecutionException("link command failed", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to execute link command", e);
        }
    }

    /** Convert Files with relative paths to be relative from the project basedir **/
    private File absolutePath(File file) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(project.getBasedir(), file.getPath());
    }
}
