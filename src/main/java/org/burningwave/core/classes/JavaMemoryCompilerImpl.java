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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

import org.burningwave.core.Closeable;
import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.classes.JavaMemoryCompiler.Compilation.Config;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

@SuppressWarnings({"rawtypes", "unchecked"})
public class JavaMemoryCompilerImpl implements JavaMemoryCompiler, Component {
	PathHelper pathHelper;
	ClassPathHelper classPathHelper;
	JavaCompiler compiler;
	FileSystemItem compiledClassesRepository;
	Properties config;
	
	JavaMemoryCompilerImpl(
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Properties config
	) {
		this.pathHelper = pathHelper;
		this.classPathHelper = classPathHelper;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.compiledClassesRepository = FileSystemItem.of(((ClassPathHelperImpl)classPathHelper).getOrCreateTemporaryFolder("compiledClassesRepository"));
		this.config = config;
	}	
	
	
	@Override
	public ProducerTask<JavaMemoryCompiler.Compilation.Result> compile(JavaMemoryCompiler.Compilation.Config config) {
		return compile(
			config.getSources(),
			getClassPathsFrom(config),
			getClassRepositoriesFrom(config),
			getBlackListedClassPaths(config),
			config.getCompiledClassesStorage(),
			config.useTemporaryFolderForStoring()
		);
	}

	private Collection<String> getBlackListedClassPaths(Config config) {
		return IterableObjectHelper.merge(
			config::getBlackListedClassPaths,
			config::getAdditionalBlackListedClassPaths,
			() -> {
				Collection<String> blackListedClassPaths = pathHelper.getPaths(
					Configuration.Key.BLACK_LISTED_CLASS_PATHS
				);
				return blackListedClassPaths;
			}
		);
	}


	Collection<String> getClassRepositoriesFrom(JavaMemoryCompiler.Compilation.Config config) {
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

	Collection<String> getClassPathsFrom(JavaMemoryCompiler.Compilation.Config config) {
		return IterableObjectHelper.merge(
			config::getClassPaths,
			config::getAdditionalClassPaths,
			() -> 
				pathHelper.getPaths(
					Configuration.Key.CLASS_PATHS
				)
		);
	}
	
	private ProducerTask<JavaMemoryCompiler.Compilation.Result> compile(
		Collection<String> sources,
		Collection<String> classPaths, 
		Collection<String> classRepositoriesPaths,
		Collection<String> blackListedClassPaths,
		String compiledClassesStorage,
		boolean useTemporaryFolderForStoring
	) {	
		return BackgroundExecutor.createTask(() -> {
			ManagedLoggersRepository.logInfo(getClass()::getName, "Try to compile: \n\n{}\n", String.join("\n", SourceCodeHandler.addLineCounter(sources)));
			Collection<MemorySource> memorySources = new ArrayList<>();
			sourcesToMemorySources(sources, memorySources);
			try (Compilation.Context context = Compilation.Context.create(
					this,
					memorySources, 
					new ArrayList<>(classPaths), 
					new ArrayList<>(classRepositoriesPaths),
					new ArrayList<>(blackListedClassPaths)
				)
			) {
				Map<String, ByteBuffer> compiledFiles = compile(context);
				String storedFilesClassPath = retrieveCompiledClassesStorage(compiledClassesStorage, useTemporaryFolderForStoring);
				if (!compiledFiles.isEmpty() && compiledClassesStorage != null ) {
					compiledFiles.forEach((className, byteCode) -> {
						JavaClass.use(byteCode, (javaClass) -> javaClass.storeToClassPath(storedFilesClassPath));
					});
				}
				Collection<String> classNames = compiledFiles.keySet();
				ManagedLoggersRepository.logInfo(getClass()::getName, 
					classNames.size() > 1?	
						"Classes {} have been succesfully compiled":
						"Class {} has been succesfully compiled",
					classNames.size() > 1?		
						String.join(", ", classNames):
						classNames.stream().findFirst().orElseGet(() -> "")
				);
				return new JavaMemoryCompiler.Compilation.Result(
					storedFilesClassPath  != null ? FileSystemItem.ofPath(storedFilesClassPath) : null, 
					compiledFiles, new HashSet<>(context.classPaths)
				);
			}
		}).submit();
	}


	private String retrieveCompiledClassesStorage(String compiledClassesStorage, boolean useTemporaryFolderForStoring) {
		String storedFilesClassPath = null;
		if (compiledClassesStorage != null) {
			if (useTemporaryFolderForStoring) {
				storedFilesClassPath = compiledClassesRepository.getAbsolutePath() + "/" + compiledClassesStorage;
			} else {
				storedFilesClassPath = compiledClassesStorage;
			}
		}
		return storedFilesClassPath;
	}	
	
	private void sourcesToMemorySources(Collection<String> sources, Collection<MemorySource> memorySources) {
		for (String source : sources) {
			String className = SourceCodeHandler.extractClassName(source);
			try {
				memorySources.add(new MemorySource(Kind.SOURCE, className, source));
			} catch (URISyntaxException exc) {
				throw new JavaMemoryCompiler.Compilation.Exception(Strings.compile("Class name \"{}\" is not valid", className), exc);
			}
		}
		
	}

	private Map<String, ByteBuffer> compile(Compilation.Context context) {
		if (!context.classPaths.isEmpty()) {
			ManagedLoggersRepository.logInfo(getClass()::getName, "... Using class paths:\n\t{}",String.join("\n\t", context.classPaths));
		}
		List<String> options = new ArrayList<>();
		if (!context.options.isEmpty()) {
			context.options.forEach((key, val) -> {
				options.add(key);
				Optional.ofNullable(val).ifPresent(value -> {
					options.add(value);
				});
				
			});
		}
		try (MemoryFileManager memoryFileManager = new MemoryFileManager(compiler)) {
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
	
	@Override
	public void close() {
		closeResources(() -> compiledClassesRepository == null, () -> {
			compiledClassesRepository.destroy();
			compiledClassesRepository = null;
			compiler = null;
			pathHelper = null;
		});
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
			if (context.diagnositListenerInterceptedMessages.contains(message)) {
				throw new JavaMemoryCompiler.Compilation.Exception(message);
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
					ManagedLoggersRepository.logError(getClass()::getName, exc);
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
						ManagedLoggersRepository.logError(getClass()::getName, exc);
					}
				} else {
					throw new JavaMemoryCompiler.Compilation.Exception(message);
				}
			}
			if (fsObjects == null || fsObjects.isEmpty()) {
				String classNameOrSimpleName = classNameOrSimpleNameTemp;				
				throw new JavaMemoryCompiler.Compilation.Exception(
					Optional.ofNullable(javaClassPredicate).map(jCP -> "Class or package \"" + classNameOrSimpleName + "\" not found").orElseGet(() -> message)
				);
			}
			fsObjects.forEach((fsObject) -> {
				context.addToClassPath(fsObject);
			});
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


	static class Compilation {
		
		static class Context implements Closeable, ManagedLogger {
			
			Collection<String> classPaths;
			Collection<String> blackListedClassPaths;
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
				Collection<String> classRepositories,
				Collection<String>  blackListedClassPaths
			) {
				this.javaMemoryCompiler = javaMemoryCompiler;
				options =  new LinkedHashMap<>();
				this.classPaths = new HashSet<>();
				this.blackListedClassPaths = new HashSet<>(blackListedClassPaths);
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
				Collection<String> classRepositories,
				Collection<String> blackListedClassPaths
			) {
				return new Context(javaMemoryCompiler, sources, classPaths, classRepositories, blackListedClassPaths);
			}
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					if (blackListedClassPaths.contains(path)) {
						logWarn("Could not add {} to class path because it is black listed", path);
						return;
					}
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
		
	}
}