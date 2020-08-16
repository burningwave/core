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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class JavaMemoryCompiler implements Component {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-paths";
			public static final String ADDITIONAL_CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-class-paths";
			public static final String CLASS_REPOSITORIES =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-repositories";
			public static final String ADDITIONAL_CLASS_REPOSITORIES =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-class-repositories";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(
				Key.CLASS_PATHS,
				PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + PathHelper.Configuration.Key.PATHS_SEPARATOR + 
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_PATHS_EXTENSION + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR + 
				"${" + Configuration.Key.ADDITIONAL_CLASS_PATHS + "}"
			);
			DEFAULT_VALUES.put(
				Key.CLASS_REPOSITORIES,
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_REPOSITORIES + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR +
				"${" + Configuration.Key.ADDITIONAL_CLASS_REPOSITORIES + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR
			);
			
			
		}
	}
	
	private PathHelper pathHelper;
	private ClassPathHelper classPathHelper;
	private JavaCompiler compiler;
	private FileSystemItem compiledClassesRepository;
	private Properties config;	
	
	private JavaMemoryCompiler(
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Properties config
	) {
		this.pathHelper = pathHelper;
		this.classPathHelper = classPathHelper;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.compiledClassesRepository = FileSystemItem.of(classPathHelper.getOrCreateTemporaryFolder("compiledClassesRepository"));
		this.config = config;
		listenTo(config);
	}	
	
	public static JavaMemoryCompiler create(
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Properties config
	) {
		return new JavaMemoryCompiler(pathHelper, classPathHelper, config);
	}
	
	public 	ProducerTask<Compilation.Result> compile(Collection<String> sources) {
		return compile(sources, true);
	}
	
	public 	ProducerTask<Compilation.Result> compile(Collection<String> sources, boolean storeCompiledClasses) {
		return compile(
			CompileConfig.withSources(sources).setClassPaths(
				pathHelper.getAllMainClassPaths()
			).storeCompiledClasses(
				storeCompiledClasses
			)
		);
	}
	
	
	
	public ProducerTask<Compilation.Result> compile(CompileConfig config) {
		return compile(
			config.getSources(),
			getClassPathsFrom(config),
			getClassRepositoriesFrom(config),
			config.isStoringCompiledClassesEnabled(),
			config.isStoringCompiledClassesToNewFolderEnabled()
		);
	}

	Collection<String> getClassRepositoriesFrom(CompileConfig config) {
		return IterableObjectHelper.merge(
			config::getClassRepositories,
			config::getAdditionalClassRepositories,
			() -> {
				Collection<String> classRepositories = pathHelper.getPaths(
					Configuration.Key.CLASS_REPOSITORIES
				);
				return classRepositories;
			}
		);
	}

	Collection<String> getClassPathsFrom(CompileConfig config) {
		return IterableObjectHelper.merge(
			config::getClassPaths,
			config::getAdditionalClassPaths,
			() -> 
				pathHelper.getPaths(
					Configuration.Key.CLASS_PATHS
				)
		);
	}
	
	private ProducerTask<Compilation.Result> compile(
		Collection<String> sources, 
		Collection<String> classPaths, 
		Collection<String> classRepositoriesPaths,
		boolean storeCompiledClasses,
		boolean storeCompiledClassesToNewFolder
	) {	
		return BackgroundExecutor.createTask(() -> {
			logInfo("Try to compile: \n\n{}\n",String.join("\n", sources));
			Collection<JavaMemoryCompiler.MemorySource> memorySources = new ArrayList<>();
			sourcesToMemorySources(sources, memorySources);
			try (Compilation.Context context = Compilation.Context.create(
					this,
					memorySources, 
					new ArrayList<>(classPaths), 
					new ArrayList<>(classRepositoriesPaths)
				)
			) {
				Map<String, ByteBuffer> compiledFiles = compile(context);
				String storedFilesClassPath = compiledClassesRepository.getAbsolutePath() + 
					(storeCompiledClassesToNewFolder?
						"/" + UUID.randomUUID().toString() :
						"");
				if (!compiledFiles.isEmpty() && storeCompiledClasses) {
					compiledFiles.forEach((className, byteCode) -> {
						JavaClass.use(byteCode, (javaClass) -> javaClass.storeToClassPath(storedFilesClassPath));
					});
				}
				Collection<String> classNames = compiledFiles.keySet();
				logInfo(
					classNames.size() > 1?	
						"Classes {} have been succesfully compiled":
						"Class {} has been succesfully compiled",
					classNames.size() > 1?		
						String.join(", ", classNames):
						classNames.stream().findFirst().orElseGet(() -> "")
				);
				return new Compilation.Result(FileSystemItem.ofPath(storedFilesClassPath), compiledFiles, new HashSet<>(context.classPaths));
			}
		}).async().submit();
	}	
	
	private void sourcesToMemorySources(Collection<String> sources, Collection<MemorySource> memorySources) {
		for (String source : sources) {
			String className = SourceCodeHandler.extractClassName(source);
			try {
				memorySources.add(new MemorySource(Kind.SOURCE, className, source));
			} catch (URISyntaxException eXC) {
				throw Throwables.toRuntimeException("Class name \"" + className + "\" is not valid");
			}
		}
		
	}


	private Map<String, ByteBuffer> compile(Compilation.Context context) {
		if (!context.classPaths.isEmpty()) {
			logInfo("... Using class paths:\n\t{}",String.join("\n\t", context.classPaths));
		}
		List<String> options = new ArrayList<String>();
		if (!context.options.isEmpty()) {
			context.options.forEach((key, val) -> {
				options.add(key);
				Optional.ofNullable(val).ifPresent(value -> {
					options.add(value);
				});
				
			});
		}
		try (JavaMemoryCompiler.MemoryFileManager memoryFileManager = new MemoryFileManager(compiler)) {
			CompilationTask task = compiler.getTask(
				null, memoryFileManager,
				new DiagnosticListener(context), options, null,
				new ArrayList<>(context.sources)
			);
			boolean done = false;
			try {
				done = task.call();
			} catch (Throwable currentException) {
				Throwable previousException = context.getPreviousException();
				if (previousException != null && previousException.getMessage().equals(currentException.getMessage())) {
					throw currentException;
				}
				context.setPreviousException(currentException);
			}
			if (!done) {
				return compile(context);
			} else {
				return memoryFileManager.getCompiledFiles().stream().collect(
					Collectors.toMap(compiledFile -> 
						compiledFile.getName(), compiledFile ->
						compiledFile.toByteBuffer()
					)
				);
			}
		}
	}
	
	static class DiagnosticListener implements javax.tools.DiagnosticListener<JavaFileObject>, Serializable, Component {

		private static final long serialVersionUID = 4404913684967693355L;
		
		private Compilation.Context context;
		
		DiagnosticListener (Compilation.Context context) {
			this.context = context;
		}
		
		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			String message = diagnostic.getMessage(Locale.ENGLISH);
			if (context.getPreviousDiagnosticMessage() != null && 
				context.getPreviousDiagnosticMessage().equals(message)
			) {
				throw new UnknownCompilerErrorMessageException(message);
			}
			context.setPreviousDiagnosticMessage(message);
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
				} catch (Exception e) {
					logError("Exception occurred", e);
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
					} catch (Exception e) {
						logError("Exception occurred", e);
					}
				} else {
					throw new UnknownCompilerErrorMessageException(message);
				}
			}
			if (fsObjects == null || fsObjects.isEmpty()) {
				String classNameOrSimpleName = classNameOrSimpleNameTemp;				
				throw Throwables.toRuntimeException(
					Optional.ofNullable(javaClassPredicate).map(jCP -> "Class or package \"" + classNameOrSimpleName + "\" not found").orElseGet(() -> message)
				);
			}
			fsObjects.forEach((fsObject) -> {
				context.addToClassPath(fsObject);
			});
		}
		
	}
	
	public static class UnknownCompilerErrorMessageException extends RuntimeException {

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
	    		ThrowingRunnable.run(() -> {
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
			ThrowingRunnable.run(() -> {
				super.close();
			});
		}
	}
	
	public static class Compilation {
		private static class Context implements Component {
			
			private Collection<String> classPaths;
			private Map<String, String> options;
			private Collection<MemorySource> sources;
			private Collection<String> classRepositories;
			private JavaMemoryCompiler javaMemoryCompiler;
			private String previousDiagnosticMessage;
			private Throwable previousException;
			
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					String classPath = Paths.clean(path);
					options.put("-classpath", Optional.ofNullable(options.get("-classpath")).orElse("") + classPath + System.getProperty("path.separator"));
					classPaths.add(classPath);
				}
			}
			
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
			}
			
			private static Context create(
				JavaMemoryCompiler javaMemoryCompiler,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				return new Context(javaMemoryCompiler, sources, classPaths, classRepositories);
			}
			
			Collection<String> findForPackageName(String packageName) throws Exception {
				Collection<String> classPaths = new HashSet<>(
					javaMemoryCompiler.classPathHelper.compute(
						Arrays.asList(javaMemoryCompiler.compiledClassesRepository.getAbsolutePath()),
						fileSystemItem ->
							fileSystemItem.getAbsolutePath().equals(
								javaMemoryCompiler.compiledClassesRepository.getAbsolutePath()
							),
						(classFile, javaClass) ->
							Objects.equals(javaClass.getPackageName(), packageName)		
					).get().values()
				);
				if (classPaths.isEmpty()) {
					classPaths.addAll(
						javaMemoryCompiler.classPathHelper.computeFromSources(
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
						javaMemoryCompiler.classPathHelper.compute(
							Arrays.asList(javaMemoryCompiler.compiledClassesRepository.getAbsolutePath()),
							fileSystemItem ->
								fileSystemItem.getAbsolutePath().equals(
									javaMemoryCompiler.compiledClassesRepository.getAbsolutePath()
								),
							(classFile, javaClass) ->
								classPredicate.test(javaClass)
						).get().values()
					);
					if (classPaths.isEmpty()) {
						classPaths.addAll(
							javaMemoryCompiler.classPathHelper.computeFromSources(
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
			
			void setPreviousDiagnosticMessage(String previousDiagnosticMessage) {
				this.previousDiagnosticMessage = previousDiagnosticMessage;
			}
			
			String getPreviousDiagnosticMessage() {
				return previousDiagnosticMessage;
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
			}

		}
		
		public static class Result implements AutoCloseable {
			private FileSystemItem classPath;
			private Map<String, ByteBuffer> compiledFiles;
			private Collection<String> dependencies;
			
			
			private Result(FileSystemItem classPath, Map<String, ByteBuffer> compiledFiles, Collection<String> classPaths) {
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


			public void close() {
				compiledFiles.clear();
				dependencies.clear();
				classPath = null;
			}	
			
		}
	}
	
	@Override
	public void close() {
		closeResources(() -> compiledClassesRepository == null, () -> {
			unregister(config);
			compiledClassesRepository.destroy();
			compiledClassesRepository = null;
			compiler = null;
			pathHelper = null;
			config = null;
		});
	}
	
}