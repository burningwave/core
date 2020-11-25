/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

import org.burningwave.core.Closeable;
import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;

public interface JavaMemoryCompiler {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-paths";
			public static final String ADDITIONAL_CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-class-paths";
			public static final String CLASS_REPOSITORIES =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-repositories";
			public static final String ADDITIONAL_CLASS_REPOSITORIES =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-class-repositories";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(
				Key.CLASS_PATHS,
				PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + PathHelper.Configuration.getPathsSeparator() + 
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_PATHS_EXTENSION + "}" + PathHelper.Configuration.getPathsSeparator() + 
				"${" + Configuration.Key.ADDITIONAL_CLASS_PATHS + "}"
			);
			defaultValues.put(
				Key.CLASS_REPOSITORIES,
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_REPOSITORIES + "}" + PathHelper.Configuration.getPathsSeparator() +
				"${" + Configuration.Key.ADDITIONAL_CLASS_REPOSITORIES + "}" + PathHelper.Configuration.getPathsSeparator()
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static JavaMemoryCompiler create(
		PathHelper pathHelper,
		ClassPathHelper classPathHelper
	) {
		return new JavaMemoryCompilerImpl(pathHelper, classPathHelper);
	}
	
	public 	ProducerTask<Compilation.Result> compile(Collection<String> sources);
	
	public 	ProducerTask<Compilation.Result> compile(Collection<String> sources, boolean storeCompiledClasses);
	
	public ProducerTask<Compilation.Result> compile(Compilation.Config config);

	static class DiagnosticListener implements javax.tools.DiagnosticListener<JavaFileObject>, Serializable, Component {
		
		private static final long serialVersionUID = 4404913684967693355L;
		
		private Compilation.Context context;
		
		DiagnosticListener (Compilation.Context context) {
			this.context = context;
		}
		
		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			String message = diagnostic.getMessage(Locale.ENGLISH);
			if (context.diagnositListenerInterceptedMessages.contains(message)) {
				Throwables.throwException(new UnknownCompilerErrorMessageException(message));
			} else {
				context.diagnositListenerInterceptedMessages.add(message);
			}
			if (message.contains("unchecked or unsafe operations") || message.contains("Recompile with -Xlint:unchecked")) {
				context.options.put("-Xlint:", "unchecked");
				return;
			}
			Collection<String> fsObjects = null;
			String classNameOrSimpleNameTemp = null;
			Predicate<JavaClass> javaClassPredicate = null;
			
			if (message.indexOf("class file for") != -1 && message.indexOf("not found") != -1) {
				classNameOrSimpleNameTemp = message.substring(message.indexOf("for ") + 4);
				classNameOrSimpleNameTemp = classNameOrSimpleNameTemp.substring(0, classNameOrSimpleNameTemp.indexOf(" "));
				final String className = classNameOrSimpleNameTemp;
				javaClassPredicate = (cls) -> cls.getName().equals(className);
			} else if(message.indexOf("class ") != -1 && message.indexOf("package ") != -1 ){
				classNameOrSimpleNameTemp = message.substring(message.indexOf("class ")+6);
				classNameOrSimpleNameTemp = classNameOrSimpleNameTemp.substring(0, classNameOrSimpleNameTemp.indexOf("\n"));
				String packageName = message.substring(message.indexOf("package") + 8);
				final String className = packageName+"."+classNameOrSimpleNameTemp;
				javaClassPredicate = (cls) -> cls.getName().equals(className);
			} else if(message.indexOf("symbol: class") != -1) {
				classNameOrSimpleNameTemp = message.substring(message.indexOf("class ")+6);
				final String classSimpleName = classNameOrSimpleNameTemp;
				javaClassPredicate =  (cls) -> cls.getSimpleName().equals(classSimpleName);
			}			
			
			if (javaClassPredicate != null) {
				try {
					fsObjects = context.findForClassName(javaClassPredicate);
				} catch (Exception exc) {
					logError(exc);
				}
			} else {
				String packageName = null;
				if (message.indexOf("package exists in another module") == -1 && message.indexOf("cannot be accessed from outside package") == -1) {
					if (message.indexOf("package ") != -1){
						packageName = message.substring(message.indexOf("package") + 8);
						int firstOccOfSpaceIdx = packageName.indexOf(" ");
						if(firstOccOfSpaceIdx!=-1) {
							packageName = packageName.substring(0, firstOccOfSpaceIdx);
						}
					}
				}
				if (Strings.isNotEmpty(packageName)) {			
					try {
						fsObjects = context.findForPackageName(packageName);
					} catch (Exception exc) {
						logError(exc);
					}
				} else {
					Throwables.throwException(new UnknownCompilerErrorMessageException(message));
				}
			}
			if (fsObjects == null || fsObjects.isEmpty()) {
				String classNameOrSimpleName = classNameOrSimpleNameTemp;				
				Throwables.throwException(
					Optional.ofNullable(javaClassPredicate).map(jCP -> "Class or package \"" + classNameOrSimpleName + "\" not found").orElseGet(() -> message)
				);
			}
			fsObjects.forEach((fsObject) -> {
				context.addToClassPath(fsObject);
			});
		}
		
	}
	
	public static class UnknownCompilerErrorMessageException extends Exception {

		private static final long serialVersionUID = 1149980549799104408L;

		public UnknownCompilerErrorMessageException(String s) {
			super(s);
		}
	}		
	
	static class MemorySource extends SimpleJavaFileObject implements Serializable {

		private static final long serialVersionUID = 4669403234662034315L;
		
		private final String content;
		private final String name;
		
	    final static String PREFIX = "memo:///";
	    public MemorySource(Kind kind, String name, String content) throws URISyntaxException {
	        super(new URI(PREFIX + name.replace('.', '/') + kind.extension), kind);
	        this.name = name;
	        this.content = content;
	    }
	    
	    @Override
		public String getName() {
	    	return this.name;
	    }
	    
	    @Override
	    public CharSequence getCharContent(boolean ignore) {
	        return this.content;
	    }
	    
	    public String getContent() {
	        return this.content;
	    }
	}
	
	static class MemoryFileObject extends SimpleJavaFileObject implements Component {
		
		private ByteBufferOutputStream baos = new ByteBufferOutputStream(false);
		private final String name;
		
	    MemoryFileObject(String name, Kind kind) {
	        super(URI.create("memory:///" + name.replace('.', '/') + kind.extension), kind);
	        this.name = name;
	    }
	    
	    public String getPath() {
	    	return uri.getPath();
	    }
	    
	    @Override
		public String getName() {
	    	return this.name;
	    }
	    
	    public ByteBuffer toByteBuffer() {
	    	return baos.toByteBuffer();
	    }
	    
	    public byte[] toByteArray() {
	    	return baos.toByteArray();
	    }

	    @Override
	    public ByteBufferOutputStream openOutputStream() {
	        return this.baos;
	    }
	    
	    @Override
		public void close() {
	    	if (baos != null) {
	    		Executor.run(() -> {
	    			baos.markAsCloseable(true);
					baos.close();
				});
	    	}
	    	baos = null;    	
	    }
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static class MemoryFileManager extends ForwardingJavaFileManager implements Component {
		
		private List<MemoryFileObject> compiledFiles;
				
		MemoryFileManager(JavaCompiler compiler) {
	        super(compiler.getStandardFileManager(null, null, null));
	        compiledFiles = new CopyOnWriteArrayList<>();
	    }
		
		@Override
	    public MemoryFileObject getJavaFileForOutput
	            (Location location, String name, Kind kind, FileObject source) {
	        MemoryFileObject mc = new MemoryFileObject(name, kind);
	        this.compiledFiles.add(mc);
	        return mc;
	    }
		
		List<MemoryFileObject> getCompiledFiles() {
			return compiledFiles;
		}
		
		@Override
		public void close() {
			compiledFiles.forEach(compiledFile -> 
				compiledFile.close()
			);
			compiledFiles.clear();
			Executor.run(() -> {
				super.close();
			});
		}
	}
	
	public static class Compilation {
		
		public static class Config {
			private Collection<String> sources;
			
			private Collection<String> classPaths;
			private Collection<String> additionalClassPaths;
			
			private Collection<String> classRepositories;
			private Collection<String> additionalClassRepositories;
			
			private String compiledClassesStorage;
			
			private Config() {
				this.sources = new HashSet<>();
				compiledClassesStorage = "/common";
			}
			
			@SafeVarargs
			public final static Config withSources(Collection<String>... sourceCollections) {
				Config compileConfig = new Config();
				for (Collection<String> sourceCollection : sourceCollections) {
					compileConfig.sources.addAll(sourceCollection);
				}
				return compileConfig;
			}
			
			@SafeVarargs
			public final static Config withSource(String... sources) {
				return withSources(Arrays.asList(sources));
			}
			
			public Config storeCompiledClasses(boolean flag) {
				if (flag) {
					compiledClassesStorage = "common";
				} else {
					compiledClassesStorage = null;
				}
				return this;
			}
			
			public Config storeCompiledClassesTo(String folderName) {
				compiledClassesStorage = folderName;
				return this;
			}
			
			public Config storeCompiledClassesToNewFolder() {
				compiledClassesStorage = UUID.randomUUID().toString();
				return this;
			}


		////////////////////	
			
			@SafeVarargs
			public final Config setClassPaths(Collection<String>... classPathCollections) {
				if (classPaths == null) {
					classPaths = new HashSet<>();
				}
				for (Collection<String> classPathCollection : classPathCollections) {
					classPaths.addAll(classPathCollection);
				}
				return this;
			}
			
			@SafeVarargs
			public final Config setClassPaths(String... classPaths) {
				return setClassPaths(Arrays.asList(classPaths));
			}

		////////////////////	
			
			@SafeVarargs
			public final Config addClassPaths(Collection<String>... classPathCollections) {
				if (additionalClassPaths == null) {
					additionalClassPaths = new HashSet<>();
				}
				for (Collection<String> classPathCollection : classPathCollections) {
					additionalClassPaths.addAll(classPathCollection);
				}
				return this;
			}
			
			@SafeVarargs
			public final Config addClassPaths(String... classPaths) {
				return addClassPaths(Arrays.asList(classPaths));
			}

		////////////////////	
			
			@SafeVarargs
			public final Config setClassRepositories(Collection<String>... classPathCollections) {
				if (classRepositories == null) {
					classRepositories = new HashSet<>();
				}
				for (Collection<String> classPathCollection : classPathCollections) {
					classRepositories.addAll(classPathCollection);
				}
				return this;
			}
			
			@SafeVarargs
			public final Config setClassRepository(String... classPaths) {
				return setClassRepositories(Arrays.asList(classPaths));
			}

		////////////////////	
			
			@SafeVarargs
			public final Config addClassRepositories(Collection<String>... classPathCollections) {
				if (additionalClassRepositories == null) {
					additionalClassRepositories = new HashSet<>();
				}
				for (Collection<String> classPathCollection : classPathCollections) {
					additionalClassRepositories.addAll(classPathCollection);
				}
				return this;
			}
			
			@SafeVarargs
			public final Config addClassRepository(String... classPaths) {
				return addClassRepositories(Arrays.asList(classPaths));
			}

		////////////////////	
			
			Collection<String> getSources() {
				return sources;
			}

			Collection<String> getClassPaths() {
				return classPaths;
			}

			Collection<String> getAdditionalClassPaths() {
				return additionalClassPaths;
			}

			Collection<String> getClassRepositories() {
				return classRepositories;
			}

			Collection<String> getAdditionalClassRepositories() {
				return additionalClassRepositories;
			}
			
			boolean isStoringCompiledClassesEnabled() {
				return compiledClassesStorage != null;
			}
			
			String getCompiledClassesStorage() {
				return compiledClassesStorage;
			}
		}
		
		static class Context implements Closeable, ManagedLogger {
			
			Collection<String> classPaths;
			Map<String, String> options;
			Collection<MemorySource> sources;
			private Collection<String> classRepositories;
			private JavaMemoryCompiler javaMemoryCompiler;
			private Throwable previousException;
			private Collection<String> diagnositListenerInterceptedMessages;
			
			private Context(
				JavaMemoryCompiler javaMemoryCompiler,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				this.javaMemoryCompiler = javaMemoryCompiler;
				options =  new LinkedHashMap<>();
				this.classPaths = new HashSet<>();
				this.sources = sources;
				if (classPaths != null) {
					for(String classPath : classPaths) {
						addToClassPath(classPath);
					}
				}
				this.classRepositories = classRepositories;
				this.diagnositListenerInterceptedMessages = new HashSet<>();
			}
			
			static Context create(
				JavaMemoryCompiler javaMemoryCompiler,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				return new Context(javaMemoryCompiler, sources, classPaths, classRepositories);
			}
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					String classPath = Paths.clean(path);
					options.put("-classpath", Optional.ofNullable(options.get("-classpath")).orElse("") + classPath + System.getProperty("path.separator"));
					classPaths.add(classPath);
				}
			}
			
			Collection<String> findForPackageName(String packageName) throws Exception {
				Collection<String> classPaths = new HashSet<>(
					((JavaMemoryCompilerImpl)javaMemoryCompiler).classPathHelper.compute(
						Arrays.asList(((JavaMemoryCompilerImpl)javaMemoryCompiler).compiledClassesRepository.getAbsolutePath()),
						fileSystemItem ->
							fileSystemItem.getAbsolutePath().equals(
								((JavaMemoryCompilerImpl)javaMemoryCompiler).compiledClassesRepository.getAbsolutePath()
							),
						(classFile, javaClass) ->
							Objects.equals(javaClass.getPackageName(), packageName)		
					).get().values()
				);
				if (classPaths.isEmpty()) {
					classPaths.addAll(
						((JavaMemoryCompilerImpl)javaMemoryCompiler).classPathHelper.computeFromSources(
							sources.stream().map(ms -> ms.getContent()).collect(Collectors.toCollection(HashSet::new)),
							classRepositories,
							null,
							(classFile, javaClass) ->
								Objects.equals(javaClass.getPackageName(), packageName)	
						).get().values()
					);
				}
				return classPaths;
			}
			
			Collection<String> findForClassName(Predicate<JavaClass> classPredicate) throws Exception {
				Collection<String> classPaths = new HashSet<>(
						((JavaMemoryCompilerImpl)javaMemoryCompiler).classPathHelper.compute(
							Arrays.asList(((JavaMemoryCompilerImpl)javaMemoryCompiler).compiledClassesRepository.getAbsolutePath()),
							fileSystemItem ->
								fileSystemItem.getAbsolutePath().equals(
									((JavaMemoryCompilerImpl)javaMemoryCompiler).compiledClassesRepository.getAbsolutePath()
								),
							(classFile, javaClass) ->
								classPredicate.test(javaClass)
						).get().values()
					);
					if (classPaths.isEmpty()) {
						classPaths.addAll(
							((JavaMemoryCompilerImpl)javaMemoryCompiler).classPathHelper.computeFromSources(
								sources.stream().map(ms -> ms.getContent()).collect(Collectors.toCollection(HashSet::new)),
								classRepositories,
								null,
								(classFile, javaClass) ->
									classPredicate.test(javaClass)
							).get().values()
						);
					}
					return classPaths;
			}
			
			void setPreviousException(Throwable previousException) {
				this.previousException = previousException;
			}
			
			Throwable getPreviousException() {
				return previousException;
			}
			
			@Override
			public void close() {
				options.clear();
				options = null;
				classPaths.clear();
				classPaths = null;
				sources = null;
				classRepositories.clear();
				classRepositories = null;
				javaMemoryCompiler = null;
				diagnositListenerInterceptedMessages.clear();
				diagnositListenerInterceptedMessages = null;
			}

		}
		
		public static class Result implements Closeable {
			private FileSystemItem classPath;
			private Map<String, ByteBuffer> compiledFiles;
			private Collection<String> dependencies;
			
			
			Result(FileSystemItem classPath, Map<String, ByteBuffer> compiledFiles, Collection<String> classPaths) {
				this.classPath = classPath;
				this.compiledFiles = compiledFiles;
				this.dependencies = classPaths;
			}


			public FileSystemItem getClassPath() {
				return classPath;
			}


			public Map<String, ByteBuffer> getCompiledFiles() {
				return compiledFiles;
			}


			public Collection<String> getDependencies() {
				return dependencies;
			}


			@Override
			public void close() {
				compiledFiles.clear();
				dependencies.clear();
				classPath = null;
			}	
			
		}
	}
}