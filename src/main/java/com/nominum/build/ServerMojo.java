package com.nominum.build;

import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This mojo monitors source files and recompiles templates and routes automatically when they change.
 */
@Mojo(name="watch")
public class ServerMojo extends AbstractMojo implements FileListener {

    /**
     * The maven project.
     */
    @Parameter(defaultValue="${project}",required=true,readonly=true)
    public MavenProject project;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    public MavenProjectHelper projectHelper;

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
     * Directory containing the build files.
     */
    @Parameter(defaultValue="${project.build.directory}")
    public File buildDirectory;

    /**
     * Base directory of the project.
     */
    @Parameter(defaultValue="${project.basedir}")
    public File baseDirectory;

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

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            setupMonitor();
        } catch (FileSystemException e) {
            throw new MojoExecutionException("Cannot set the file monitor on the source folder", e);
        }

        String MESSAGE = "You're running the watch mode. Modified templates and " +
                "routes will be processed automatically. \n" +
                "To leave the watch mode, just hit CTRL+C.\n";

        getLog().info(MESSAGE);

        try {
            invokeScalaCC();

            // just wait around until killed
            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            /* exit normally */
        }
    }

    class StreamConsumer extends Thread {

        private Process process;

        public StreamConsumer(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                InputStreamReader inputStream = new InputStreamReader(process.getInputStream());
                BufferedReader bufferedOut = new BufferedReader(inputStream);

                String line = null;
                while ((line = bufferedOut.readLine()) != null) {
                    // HACK filter out noise from slightly broken 3.1.0 version of the plugin
                    if (line.equals("[INFO] wait for files to compile...")
                     || line.contains("Compile success at")) continue;
                    System.out.println(line);
                }
            }
            catch (IOException e) {
                return;
            }
        }
    }

    private void invokeScalaCC() throws MojoExecutionException {
        ProcessBuilder processBuilder = new ProcessBuilder("mvn", "scala:cc");
        processBuilder.redirectErrorStream(true);
        try {
            final Process scalaCC = processBuilder.start();
            StreamConsumer streamConsumer = new StreamConsumer(scalaCC);
            streamConsumer.start();
            try {
                scalaCC.waitFor();
            } finally {
                stopProcess(scalaCC);
            }

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    stopProcess(scalaCC);
                }
            });

        } catch (IOException e) {
            throw new MojoExecutionException("Unable to find or run \"mvn\" (maven executable)", e);
        } catch (InterruptedException e) {
            throw new MojoExecutionException("Running scala:cc Failed", e);
        }
    }

    private void stopProcess(Process process) {
        try {
            process.getInputStream().close();
            process.getOutputStream().close();
            process.getErrorStream().close();
        } catch (IOException e) {
        }
        process.destroy();
    }

    private void setupMonitor() throws FileSystemException {
        getLog().info("Set up file monitor on " + sourceDirectory);
        FileSystemManager fsManager = VFS.getManager();
        FileObject dir = fsManager.resolveFile(sourceDirectory.getAbsolutePath());

        DefaultFileMonitor fm = new DefaultFileMonitor(this);
        fm.setRecursive(true);
        fm.addFile(dir);
        FileObject routes = fsManager.resolveFile(new File(confDirectory, "routes").getAbsolutePath());
        fm.addFile(routes);

        fm.start();
    }

    private void compileTemplatesAndRoutes() throws MojoExecutionException {
        RouteCompilerMojo.compileRoutes(confDirectory,
                generatedSourcesDirectory, project, generateReverseRouter);
        TemplateCompilerMojo.compileTemplates(
                generatedSourcesDirectory, project, sourceDirectory, forJava);
    }

    public void fileCreated(FileChangeEvent event) throws Exception {
        getLog().debug("New file found " + event.getFile().getName().getBaseName());

        compileTemplatesAndRoutes();
    }

    public void fileDeleted(FileChangeEvent event) throws Exception {
        getLog().debug("File " + event.getFile().getName().getBaseName() + " deleted");

        // TODO delete the corresponding class file
    }

    public void fileChanged(FileChangeEvent event) throws Exception {
        getLog().debug("File changed: " + event.getFile().getName().getBaseName());

        compileTemplatesAndRoutes();
    }
}
