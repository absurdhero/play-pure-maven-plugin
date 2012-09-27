Play Pure-Maven Plugin
======================

This plugin allows Play Framework 2.0 projects to use the Maven build system instead of SBT.

The plugin provides a solution to organizations who already have maven experience or
would like to create a web application with Play Framework that plays well
with other modules managed by maven.

Currently, the plugin simply allows maven to compile *.scala.html,
compile the routes file, and includes the "public" assets directory in your build.

This plugin is easy to test out since it is possible to add it to
an existing Play SBT project without modifying the directory structure.

Features
--------

- Does not require that Play or SBT be installed
- Compiles HTML Scala templates
- Compiles conf/routes file
- Makes static assets available in the classpath with no copying

Benefits
--------

- Supports use of play in sub-projects (modules)
- Better junit support (e.g. running cucumber-jvm through junit)
- Full maven remote repo support (whereas SBT uses Ivy which is not 100% compatible)
- Mature IDE support
- Better Jenkins integration

To-Do
-----

- Provide a hot-reloading development server through an SBTLink implementation
- Find a way to use the play version specified by the plugin consumer rather than specifying a version in this plugin
- As an Alternative, version this plugin based on the play version it is tied with
- Asset linking is only supported on unix-like systems. A solution for Windows would be nice.

License
-------

This project is licensed under the Apache License Version 2.0.
A copy of the license is available in the LICENSE file.

Maven Project Setup (pom.xml)
------------------------------

Refer to ``sample_play_project_pom.xml`` for a non-trivial maven example
project which works with the recommended Play 2.0 file layout.
It handles class paths appropriately, sets up the scala compiler, and invokes
the play-pure-maven plugin at the right points. This sample also shows how to
set up jar packaging, provides the ability to start the production server with ``mvn exec:exec``,
and shows how to correctly set up code coverage analysis for a typical play project.

If you are a Maven maven, you can take a look at the relevant additions to the POM:

In the plugins section:

::

   <plugin>
       <groupId>com.nominum.build</groupId>
       <artifactId>play-pure-maven-plugin</artifactId>
       <version>1.0-SNAPSHOT</version>
       <executions>
           <execution>
               <goals>
                   <goal>compile-templates</goal>
                   <goal>link-assets</goal>
               </goals>
           </execution>
       </executions>
   </plugin>

   <plugins>
      <plugin>
        <groupId>org.scala-tools</groupId>
        <artifactId>maven-scala-plugin</artifactId>
        <version>2.14.1</version>
        <configuration>
          <charset>UTF-8</charset>
          <jvmArgs>
            <jvmArg>-Xmx1024m</jvmArg>
          </jvmArgs>
        </configuration>
        <executions>
          <execution>
            <id>compile</id>
            <goals>
              <goal>compile</goal>
            </goals>
            <phase>compile</phase>
          </execution>
          <execution>
            <id>test-compile</id>
            <goals>
              <goal>testCompile</goal>
            </goals>
            <phase>test-compile</phase>
          </execution>
          <execution>
            <phase>process-resources</phase>
            <goals>
              <goal>compile</goal>
            </goals>
          </execution>
        </executions>
      </plugin>


In the dependencies section, include the version of Play you will depend on:

::

        <dependency>
            <groupId>play</groupId>
            <artifactId>play_2.9.1</artifactId>
            <version>2.0.3</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>play</groupId>
            <artifactId>templates_2.9.1</artifactId>
            <version>2.0.3</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>play</groupId>
            <artifactId>play-test_2.9.1</artifactId>
            <version>2.0.3</version>
            <scope>compile</scope>
        </dependency>
