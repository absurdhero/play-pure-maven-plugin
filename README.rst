Play Pure-Maven Plugin
======================

This plugin allows Play Framework 2.2 projects to use the Maven build system instead of SBT.

The plugin provides a solution to organizations who already have maven experience or
would like to create a web application with Play Framework that plays well
with other modules managed by maven.

Currently, the plugin simply allows maven to compile *.scala.html,
compile the routes file, and includes the "public" assets directory in your build.

This plugin is easy to test out since it is possible to add it to
an existing Play SBT project without modifying the directory structure.


Features
--------

- Compiles HTML Scala templates
- Compiles conf/routes file
- Makes static assets available in the classpath with no copying
- Does not require that Play or SBT be installed

Benefits
--------

- Supports use of play in sub-projects (modules)
- Better junit support (e.g. running cucumber-jvm through junit)
- Complete maven remote repo support including support for central proxies
- Leverages mature IDE support
- Easy Jenkins integration

Installing the Plugin From Source
---------------------------------

Clone the repository from git and install the plugin in your local maven repository (on disk) ::

  git clone https://github.com/absurdhero/play-pure-maven-plugin.git
  cd play-pure-maven-plugin
  mvn install -DskipTests=true

Note: tests are skipped for the first execution because they depend on the plugin itself.

Plugin Mojos
------------

In normal usage, you don't need to execute these directly.
They run automatically during the right lifecycle phases when building a project that uses this plugin.

To get much more detailed help after installing the plugin,
run ``mvn help:describe -Ddetail=true -Dplugin=net.raboof.play:play-pure-maven-plugin``

play-pure:compile-templates
  Translates scala.html templates and conf/routes into scala source files.

play-pure:link-assets
  This goal allows you to change javascript and other assets and see your changes immediately in your running server.
  It adds your public assets directory to the classpath by creating a symlink in the build output directory.

To-Do
-----

- Support continuous compilation of templates as source files are edited
- Provide a hot-reloading development server through an SBTLink implementation or other mechanism
- Find a way to use the play version specified by the plugin consumer rather than specifying a version in this plugin
  As an alternative, this plugin is versioned based on the play version it is tied with.
- Asset linking is only supported on unix-like systems. A solution for Windows would be nice.

License
-------

This project is licensed under the Apache License Version 2.0.
A copy of the license is available in the LICENSE file.

This software was originally developed at Nominum_ for internal use. Nominum allowed it to be released to the broader Play Framework community while supporting its future development by employing the author.

.. _Nominum: http://www.nominum.com/

Maven Project Setup (pom.xml)
------------------------------

A bare-bones project can be found at `src/test/resources/play-maven-project <https://github.com/absurdhero/play-pure-maven-plugin/tree/master/src/test/resources/play-maven-project>`_
This working project uses the recommended Play 2.0 file layout.

Refer to ``sample_play_project_pom.xml`` for a non-trivial maven example
project which works with the Play 2.0 file layout.
It handles class paths appropriately, sets up the scala compiler, and invokes
the play-pure-maven plugin at the right points. This sample also shows how to
set up jar packaging, provides the ability to start the production server with ``mvn exec:exec``,
and shows how to correctly set up code coverage analysis for a typical play project.

If you are a Maven maven, you can take a look at just the essential additions to the POM:

In the plugins section:

::

   <plugin>
       <groupId>net.raboof.play</groupId>
       <artifactId>play-pure-maven-plugin</artifactId>
       <version>2.3.7</version>
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
        <groupId>net.alchim31.maven</groupId>
        <artifactId>scala-maven-plugin</artifactId>
        <version>3.1.6</version>
        <configuration>
          <!-- Use Zinc Compiler if running (https://github.com/typesafehub/zinc) -->
          <recompileMode>incremental</recompileMode>
          <useZincServer>true</useZincServer>
          <charset>UTF-8</charset>
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

Add the Typesafe Repository so the Play Framework can be downloaded:

::

      <repositories>
          <repository>
              <id>typesafe</id>
              <url>http://repo.typesafe.com/typesafe/releases/</url>
          </repository>
      </repositories>

In the dependencies section, include the version of Play you will depend on:

::

        <dependency>
            <groupId>com.typesafe.play</groupId>
            <artifactId>play_2.10</artifactId>
            <version>2.3.7</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>com.typesafe.play</groupId>
            <artifactId>templates_2.10</artifactId>
            <version>2.3.7</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>com.typesafe.play</groupId>
            <artifactId>play-test_2.10</artifactId>
            <version>2.3.7</version>
            <scope>compile</scope>
        </dependency>
