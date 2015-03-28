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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Link static assets directory into build output directory.
 *
 * This Mojo only works on Unix-like systems with the "ln" program.
 */
@Mojo(name="remove-link-assets",defaultPhase=LifecyclePhase.PRE_CLEAN)
public class RemoveLinkAssetsMojo extends AbstractMojo {

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

        // remove link if it exists
        if (linkTarget.exists() && linkTarget.isFile()) {
            boolean deleted = linkTarget.delete();
            if (!deleted) {
                throw new MojoExecutionException(
                        "Failed to delete " + linkName + " prior to linking asset directory");
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
