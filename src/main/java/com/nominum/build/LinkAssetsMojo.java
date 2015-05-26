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

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

/**
 * Link static assets directory into build output directory.
 *
 * This Mojo creates a symbolic link on unix-like systems
 * and uses mklink on Windows.
 */
@Mojo(name="link-assets",defaultPhase=LifecyclePhase.GENERATE_SOURCES)
public class LinkAssetsMojo extends AbstractMojo {

    @Parameter(defaultValue="${project}",required=true,readonly=true)
    private MavenProject project;

    /**
     * Output directory in classpath.
     */
    @Parameter(defaultValue="${project.build.outputDirectory}",required=true)
    private File outputDirectory;

    /**
     * Location of the assets directory.
     */
    @Parameter(defaultValue="${project.basedir}/public",required=true)
    private File assetDirectory;

    public void execute()
            throws MojoExecutionException {

        File outputDir = absolutePath(outputDirectory);
        File assetDir = absolutePath(assetDirectory);

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) throw new MojoExecutionException("Failed to create output directory");
        }

        String linkName = assetDir.getAbsolutePath().substring(assetDir.getParent().length() + 1);
        File linkTarget = new File(outputDir, linkName);
   
        // recreate link if it exists
        if (linkTarget.exists()) {
            boolean deleted = linkTarget.delete();
            if (!deleted) {
                throw new MojoExecutionException(
                        "Failed to delete " + linkName + " prior to linking asset directory");
            }
        }

        String[] command;
        getLog().info("OS name:" +System.getProperty("os.name"));
        if (System.getProperty("os.name").indexOf("indows")>0){
            command = new String[] {"cmd" , "/c","mklink", "/D", linkTarget.getAbsolutePath(), assetDir.getAbsolutePath()};
        } else {
            command = new String[] {"ln", "-s", assetDir.getAbsolutePath(), linkTarget.getAbsolutePath()};
        }

        try {
            getLog().info("Linking " + assetDirectory + " to " + linkTarget);

            Process proc = Runtime.getRuntime().exec(command, null, new File("."));

            int exitVal = proc.waitFor();
            if (exitVal != 0) {
                throw new MojoExecutionException("linking assets directory failed. Command: \"" + StringUtils.join(command, " ") + "\"");
            }
            getLog().info("Linking " + assetDirectory + " to " + linkTarget+" done");
        } catch (InterruptedException e) {
            throw new MojoExecutionException("link command failed", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to execute link command, run as administrator.", e);
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
