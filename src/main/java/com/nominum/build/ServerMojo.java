package com.nominum.build;

import com.nominum.play.StaticDevApplication;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import play.api.Mode;
import play.core.server.NettyServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This mojo monitors source files and recompiles and reloads the development server automatically.
 * @goal watch
 */
public class ServerMojo extends AbstractMojo implements FileListener {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
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

    /**
     * Directory containing the build files.
     * @parameter expression="${project.build.directory}"
     */
    public File buildDirectory;

    /**
     * Base directory of the project.
     * @parameter expression="${basedir}"
     */
    public File baseDirectory;

    /**
     * @parameter default-value="true"
     */
    protected boolean runServer;

    /**
     * @parameter default-value="9000"
     */
    protected int serverPort;

    /**
     * @parameter default-value="0.0.0.0"
     */
    protected String serverListenAddress;

    /**
     * The Internal Play Development Server
     */
    protected NettyServer server;

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            setupMonitor();
        } catch (FileSystemException e) {
            throw new MojoExecutionException("Cannot set the file monitor on the source folder", e);
        }

        String MESSAGE = "You're running the watch mode. Modified templates and " +
                "routes will be processed automatically. \n" +
                "To leave the watch mode, just hit CTRL+C.\n";
        if (runServer) {
            MESSAGE += "The play development server is enabled. " +
                    "It is accessible from http://localhost:" + serverPort + "/. \n";

        }
        getLog().info(MESSAGE);


        // XXX start up and compile everything

        try {
            if (runServer) {
                try {
                    startServer();
                } catch (Exception e) {
                    throw new MojoExecutionException("Cannot run the development server", e);
                }
            }

            // just wait around until killed
            invokeScalaCC();

            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            /* exit normally */
        } finally {
            if (runServer) {
                stopServer();
            }
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
                    if (line.equals("[INFO] wait for files to compile...")) continue;
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
            scalaCC.waitFor();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    scalaCC.destroy();
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to find or run \"mvn\" (maven executable)", e);
        } catch (InterruptedException e) {
            throw new MojoExecutionException("Running scala:cc Failed", e);
        }
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

    private void startServer() {
        server = new NettyServer(
                new StaticDevApplication(baseDirectory),
                serverPort,
                serverListenAddress,
                Mode.Dev());
    }

    private void stopServer() {
        server.stop();
    }

    private void restartServer() {
        if (runServer) {
            stopServer();
            startServer();
        }
    }

    private void compileTemplatesAndRoutes() throws MojoExecutionException {
        TemplateCompilerMojo.compileTemplatesAndRoutes(confDirectory,
                generatedSourcesDirectory, project, sourceDirectory);
    }

    public void fileCreated(FileChangeEvent event) throws Exception {
        getLog().info("New file found " + event.getFile().getName().getBaseName());

        compileTemplatesAndRoutes();
        restartServer();

    }

    public void fileDeleted(FileChangeEvent event) throws Exception {
        getLog().info("File " + event.getFile().getName().getBaseName() + " deleted");

        // TODO delete the corresponding class file and reload (if server running)
        restartServer();

    }

    public void fileChanged(FileChangeEvent event) throws Exception {
        getLog().info("File changed: " + event.getFile().getName().getBaseName());

        compileTemplatesAndRoutes();
        restartServer();
    }
}
