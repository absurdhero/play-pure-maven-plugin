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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import play.core.enhancers.PropertiesEnhancer.GeneratedAccessor;
import play.core.enhancers.PropertiesEnhancer.GeneratedGetAccessor;
import play.core.enhancers.PropertiesEnhancer.GeneratedSetAccessor;
import play.core.enhancers.PropertiesEnhancer.RewrittenAccessor;

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
		getLog().debug("classpath: " + classpath);
		getLog().info("Generating getters and setters.");
		enhanceProperties(project, absolutePath(outputDirectory), classpath);
		getLog().info("Rewriting property accessors.");
		rewriteAccess(project, absolutePath(outputDirectory), classpath);
		if (originalSystemProperties != null) {
			System.setProperties( originalSystemProperties );
		}
		Thread.currentThread().setContextClassLoader(originalClassLoader);
	}

	
	public void enhanceProperties(MavenProject project, File targetDir, String classpath) throws MojoExecutionException {

		// get all the files from a directory
		File[] fList = targetDir.listFiles();
		getLog().debug("Directory: " + targetDir.getAbsolutePath());
		for (File classFile : fList) {
			if (classFile.isFile() && classFile.getName().endsWith(".class")) {
				try {
					generateAccessors(classpath, classFile);
				} catch (Exception e) {
					throw new MojoExecutionException("Error while generating accessors on "
							+ classFile.getAbsolutePath(), e);
				}
			} else if (classFile.isDirectory()) {
				enhanceProperties(project, classFile, classpath);
			}
		}
	}
	
	public void rewriteAccess(MavenProject project, File targetDir, String classpath) throws MojoExecutionException {
		// get all the files from a directory
		File[] fList = targetDir.listFiles();
		getLog().debug("Directory: " + targetDir.getAbsolutePath());		
		for (File classFile : fList) {
			if (classFile.isFile() && classFile.getName().endsWith(".class")) {
				try {
					rewriteAccess(classpath, classFile);
				} catch (Exception e) {
					throw new MojoExecutionException("Error while rewriting accessors on "
							+ classFile.getAbsolutePath(), e);
				}
			} else if (classFile.isDirectory()) {
				rewriteAccess(project, classFile, classpath);
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
	
	
	
    public void generateAccessors(String classpath, File classFile) throws Exception {
    	
        ClassPool classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendPathList(classpath);
        
        FileInputStream is = new FileInputStream(classFile);
        try {
            CtClass ctClass = classPool.makeClass(is);
            if(hasAnnotation(ctClass, GeneratedAccessor.class)) {
                is.close();
                getLog().debug(ctClass.getName() + " getters/setters generated, closing file");
                return;
            }
            for (CtField ctField : ctClass.getDeclaredFields()) {
                if(isProperty(ctField)) {
                    
                	getLog().debug("Found property: "+ctField.getName());
                	
                    // Property name
                    String propertyName = ctField.getName().substring(0, 1).toUpperCase() + ctField.getName().substring(1);
                    String getter = "get" + propertyName;
                    String setter = "set" + propertyName;
                    
                    SignatureAttribute signature = ((SignatureAttribute)ctField.getFieldInfo().getAttribute(SignatureAttribute.tag));

                    try {
                        CtMethod ctMethod = ctClass.getDeclaredMethod(getter);
                        getLog().debug("Found method "+ctMethod.getName());
                        if (ctMethod.getParameterTypes().length > 0 || Modifier.isStatic(ctMethod.getModifiers())) {
                            throw new NotFoundException("it's not a getter !");
                        }
                    } catch (NotFoundException noGetter) {
                        // Create getter
                    	CtMethod getMethod = CtMethod.make("public " + ctField.getType().getName() + " " + getter + "() { return this." + ctField.getName() + "; }", ctClass);
                    	getLog().debug("Generating GETTER for "+getMethod.getName() + " " +getMethod.getSignature());
                        ctClass.addMethod(getMethod);
                        createAnnotation(getAnnotations(getMethod), GeneratedAccessor.class);
                        createAnnotation(getAnnotations(ctField), GeneratedGetAccessor.class);
                        if(signature != null) {
                            String fieldSignature = signature.getSignature();
                            String getMethodSignature = "()" + fieldSignature;
                            getMethod.getMethodInfo().addAttribute(
                                new SignatureAttribute(getMethod.getMethodInfo().getConstPool(), getMethodSignature)
                            );
                        }
                    }

                    try {
                        CtMethod ctMethod = ctClass.getDeclaredMethod(setter);
                        getLog().debug("Found method "+ctMethod.getName());
                        if (ctMethod.getParameterTypes().length != 1 || !ctMethod.getParameterTypes()[0].equals(ctField.getType()) || Modifier.isStatic(ctMethod.getModifiers())) {
                            throw new NotFoundException("it's not a setter !");
                        }
                    } catch (NotFoundException noSetter) {
                        // Create setter
                        CtMethod setMethod = CtMethod.make("public void " + setter + "(" + ctField.getType().getName() + " value) { this." + ctField.getName() + " = value; }", ctClass);
                        getLog().debug("Generating SETTER for "+setMethod.getName() + " "+setMethod.getSignature());
                        ctClass.addMethod(setMethod);
                        createAnnotation(getAnnotations(setMethod), GeneratedAccessor.class);
                        createAnnotation(getAnnotations(ctField), GeneratedSetAccessor.class);
                        if(signature != null) {
                            String fieldSignature = signature.getSignature();
                            String setMethodSignature = "(" + fieldSignature + ")V";
                            setMethod.getMethodInfo().addAttribute(
                                new SignatureAttribute(setMethod.getMethodInfo().getConstPool(), setMethodSignature)
                            );
                        }
                    }
                    
                }
                
            }
            
            createAnnotation(getAnnotations(ctClass), GeneratedAccessor.class);
            
            is.close();
            FileOutputStream os = new FileOutputStream(classFile);
            os.write(ctClass.toBytecode());
            os.close();
            
        } catch(Exception e) {
            e.printStackTrace();
            try {
                is.close();
            } catch(Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
    
    public void rewriteAccess(String classpath, File classFile) throws Exception {
    	
        ClassPool classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendPathList(classpath);
        
        FileInputStream is = new FileInputStream(classFile);
        try {
            final CtClass ctClass = classPool.makeClass(is);
            if(hasAnnotation(ctClass, RewrittenAccessor.class)) {
                is.close();
                getLog().debug(ctClass.getName() + " already rewritten, closing file");
                return;
            }
            
            for (final CtBehavior ctMethod : ctClass.getDeclaredBehaviors()) {
                ctMethod.instrument(new ExprEditor() {

                    @Override
                    public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                        try {

                            // Has accessor
                            if (isAccessor(fieldAccess.getField())) {
                                
                                String propertyName = null;
                                if (fieldAccess.getField().getDeclaringClass().equals(ctMethod.getDeclaringClass())
                                    || ctMethod.getDeclaringClass().subclassOf(fieldAccess.getField().getDeclaringClass())) {
                                    if ((ctMethod.getName().startsWith("get") || ctMethod.getName().startsWith("set")) && ctMethod.getName().length() > 3) {
                                        propertyName = ctMethod.getName().substring(3);
                                        propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
                                    }
                                }

                                if (propertyName == null || !propertyName.equals(fieldAccess.getFieldName())) {
                                    
                                    String getSet = fieldAccess.getFieldName().substring(0,1).toUpperCase() + fieldAccess.getFieldName().substring(1);
                                    
                                    if (fieldAccess.isReader() && hasAnnotation(fieldAccess.getField(), GeneratedGetAccessor.class)) {
                                        // Rewrite read access
                                        fieldAccess.replace("$_ = $0.get" + getSet + "();");
                                        getLog().debug(ctClass.getName()+": replacing "+propertyName+" field read access with get" + getSet);
                                    } else if (fieldAccess.isWriter() && hasAnnotation(fieldAccess.getField(), GeneratedSetAccessor.class)) {
                                        // Rewrite write access
                                        fieldAccess.replace("$0.set" + getSet + "($1);");
                                        getLog().debug(ctClass.getName()+": replacing "+propertyName+" field write access with set" + getSet);
                                    }
                                }
                            }

                        } catch (Exception e) {
                        	getLog().error("Error while generating accessors: ",e);
                        }
                    }
                });
            }
            
            createAnnotation(getAnnotations(ctClass), RewrittenAccessor.class);
            
            is.close();
            FileOutputStream os = new FileOutputStream(classFile);
            os.write(ctClass.toBytecode());
            os.close();
            
        } catch(Exception e) {
            e.printStackTrace();
            try {
                is.close();
            } catch(Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
    static boolean isProperty(CtField ctField) {
        if (ctField.getName().equals(ctField.getName().toUpperCase()) || ctField.getName().substring(0, 1).equals(ctField.getName().substring(0, 1).toUpperCase())) {
            return false;
        }
        return Modifier.isPublic(ctField.getModifiers())
                && !Modifier.isFinal(ctField.getModifiers())
                && !Modifier.isStatic(ctField.getModifiers());
    }
    
    static boolean isAccessor(CtField ctField) throws Exception {
        return hasAnnotation(ctField, GeneratedGetAccessor.class) || hasAnnotation(ctField, GeneratedSetAccessor.class);
    }
    
    // --
    
    /**
     * Test if a class has the provided annotation 
     */
    static boolean hasAnnotation(CtClass ctClass, Class<? extends java.lang.annotation.Annotation> annotationType) throws ClassNotFoundException {
        return getAnnotations(ctClass).getAnnotation(annotationType.getName()) != null;
    }

    /**
     * Test if a field has the provided annotation 
     */    
    static boolean hasAnnotation(CtField ctField, Class<? extends java.lang.annotation.Annotation> annotationType) throws ClassNotFoundException {
        return getAnnotations(ctField).getAnnotation(annotationType.getName()) != null;
    }
    
    /**
     * Retrieve all class annotations.
     */
    static AnnotationsAttribute getAnnotations(CtClass ctClass) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctClass.getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctClass.getClassFile().getConstPool(), AnnotationsAttribute.visibleTag);
            ctClass.getClassFile().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }
    
    /**
     * Retrieve all field annotations.
     */    
    static AnnotationsAttribute getAnnotations(CtField ctField) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctField.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctField.getFieldInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            ctField.getFieldInfo().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }

    /**
     * Retrieve all method annotations.
     */    
    static AnnotationsAttribute getAnnotations(CtMethod ctMethod) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctMethod.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctMethod.getMethodInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            ctMethod.getMethodInfo().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }
    
    /**
     * Create a new annotation to be dynamically inserted in the byte code.
     */
    static void createAnnotation(AnnotationsAttribute attribute, Class<? extends java.lang.annotation.Annotation> annotationType, Map<String, MemberValue> members) {
    	Annotation annotation = new Annotation(annotationType.getName(), attribute.getConstPool());
        for (Map.Entry<String, MemberValue> member : members.entrySet()) {
            annotation.addMemberValue(member.getKey(), member.getValue());
        }
        attribute.addAnnotation(annotation);
    }

    /**
     * Create a new annotation to be dynamically inserted in the byte code.
     */    
    static void createAnnotation(AnnotationsAttribute attribute, Class<? extends java.lang.annotation.Annotation> annotationType) {
        createAnnotation(attribute, annotationType, new HashMap<String, MemberValue>());
    }
}
