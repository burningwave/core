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

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class JavaMemoryCompiler implements Component {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "java-memory-compiler.class-path-hunter.search-config.check-file-option";
			public static final String MAIN_CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.main-class-paths";
			public static final String ADDITIONAL_MAIN_CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-main-class-paths";
			public static final String CLASS_REPOSITORIES =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-repositories";
			public static final String ADDITIONAL_CLASS_REPOSITORIES = PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-class-repositories";
		}
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(
				Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScannerAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			DEFAULT_VALUES.put(
				Key.MAIN_CLASS_PATHS, 
				PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + PathHelper.Configuration.Key.PATHS_SEPARATOR + 
				"${" + Configuration.Key.ADDITIONAL_MAIN_CLASS_PATHS + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR
			);
			DEFAULT_VALUES.put(
				Key.CLASS_REPOSITORIES, 
				DEFAULT_VALUES.get(Key.MAIN_CLASS_PATHS) +
				"${" + PathHelper.Configuration.Key.PATHS_PREFIX + PathHelper.Configuration.Key.MAIN_CLASS_PATHS_EXTENSION + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR + 
				"${" + Configuration.Key.ADDITIONAL_CLASS_REPOSITORIES + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR 
			);
		}
	}
	
	private PathHelper pathHelper;
	private ClassPathHunter classPathHunter;
	private JavaCompiler compiler;
	private FileSystemItem compiledClassesClassPath;
	private FileSystemItem classPathHunterBasePathForCompressedLibs;
	private FileSystemItem classPathHunterBasePathForCompressedClasses;
	private Properties config;	
	
	private JavaMemoryCompiler(
		PathHelper pathHelper,
		ClassPathHunter classPathHunter,
		Properties config
	) {
		this.pathHelper = pathHelper;
		this.classPathHunter = classPathHunter;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.compiledClassesClassPath = FileSystemItem.of(getOrCreateTemporaryFolder("compiled"));
		this.classPathHunterBasePathForCompressedLibs = FileSystemItem.of(getOrCreateTemporaryFolder("lib"));
		this.classPathHunterBasePathForCompressedClasses = FileSystemItem.of(getOrCreateTemporaryFolder("classes"));
		this.config = config;
		listenTo(config);
	}	
	
	public static JavaMemoryCompiler create(
		PathHelper pathHelper,
		ClassPathHunter classPathHunter,
		Properties config
	) {
		return new JavaMemoryCompiler(pathHelper, classPathHunter, config);
	}
	
	public 	Map<String, ByteBuffer> compile(Collection<String> sources) {
		return compile(sources, true);
	}
	
	public 	Map<String, ByteBuffer> compile(Collection<String> sources, boolean storeCompiledClasses) {
		return compile(
			sources, 
			pathHelper.getPaths(JavaMemoryCompiler.Configuration.Key.MAIN_CLASS_PATHS),
			pathHelper.getPaths(JavaMemoryCompiler.Configuration.Key.CLASS_REPOSITORIES),
			storeCompiledClasses
		);
	}
	
	public Map<String, ByteBuffer> compile(
		Collection<String> sources, 
		Collection<String> classPaths, 
		Collection<String> classRepositoriesPaths,
		boolean storeCompiledClasses
	) {
		Collection<JavaMemoryCompiler.MemorySource> memorySources = new ArrayList<>();
		sourcesToMemorySources(sources, memorySources);
		try (Compilation.Context context = Compilation.Context.create(this, classPathHunter, memorySources, new ArrayList<>(classPaths), new ArrayList<>(classRepositoriesPaths))) {
			Map<String, ByteBuffer> compiledFiles = _compile(context, null);
			if (!compiledFiles.isEmpty() && storeCompiledClasses) {
				compiledFiles.forEach((className, byteCode) -> {
					JavaClass javaClass = JavaClass.create(byteCode);
					javaClass.storeToClassPath(compiledClassesClassPath.getAbsolutePath());
				});
			}			
			return compiledFiles;
		}
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


	private Map<String, ByteBuffer> _compile(Compilation.Context context, Throwable thr) {
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
				new MemoryDiagnosticListener(context), options, null,
				new ArrayList<>(context.sources)
			);
			boolean done = false;
			Throwable exception = null;
			try {
				done = task.call();
			} catch (Throwable exc) {
				if (thr != null && thr.getMessage().equals(exc.getMessage())) {
					throw exc;
				}
				exception = exc;
			}
			if (!done) {
				return _compile(context, exception);
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
	
	static class MemoryDiagnosticListener implements DiagnosticListener<JavaFileObject>, Serializable, Component {

		private static final long serialVersionUID = 4404913684967693355L;
		
		private Compilation.Context context;
		
		MemoryDiagnosticListener (Compilation.Context context) {
			this.context = context;
		}
		
		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			String message = diagnostic.getMessage(Locale.ENGLISH);
			if (message.contains("unchecked or unsafe operations") || message.contains("Recompile with -Xlint:unchecked")) {
				context.options.put("-Xlint:", "unchecked");
				return;
			}
			Collection<FileSystemItem> fsObjects = null;
			
			Map.Entry<String, Predicate<Class<?>>> classNameAndClassPredicate = getClassPredicateBagFromErrorMessage(message);
			String packageName = null;
			if (classNameAndClassPredicate != null) {
				try {
					fsObjects = context.findForClassName(classNameAndClassPredicate.getValue());
				} catch (Exception e) {
					logError("Exception occurred", e);
				}
			} else if (Strings.isNotEmpty(packageName = getPackageNameFromErrorMessage(message))) {			
				try {
					fsObjects = context.findForPackageName(packageName);
				} catch (Exception e) {
					logError("Exception occurred", e);
				}
			} else {
				throw new UnknownCompilerErrorMessageException(message);
			}
			if (fsObjects == null || fsObjects.isEmpty()) {
				throw Throwables.toRuntimeException("Class or package \"" + classNameAndClassPredicate.getKey() + "\" not found");
			}
			fsObjects.forEach((fsObject) -> {
				if (fsObject.isCompressed()) {					
					ThrowingRunnable.run(() -> {
						synchronized (context.javaMemoryCompiler) {
							FileSystemItem classPathBasePath = fsObject.isArchive() ?
								context.javaMemoryCompiler.classPathHunterBasePathForCompressedLibs :
								context.javaMemoryCompiler.classPathHunterBasePathForCompressedClasses
							;
							FileSystemItem classPath = FileSystemItem.ofPath(
								classPathBasePath.getAbsolutePath() + "/" + fsObject.getName()
							);
							if (!classPath.refresh().exists()) {
								fsObject.copyTo(classPathBasePath.getAbsolutePath());
							}
							context.addToClassPath(
								classPath.getAbsolutePath()
							);
							
						}
					});
				} else {
					context.addToClassPath(fsObject.getAbsolutePath());
				}
			});		
		}

		private Map.Entry<String, Predicate<Class<?>>> getClassPredicateBagFromErrorMessage(String message) {
			if (message.indexOf("class file for") != -1 && message.indexOf("not found") != -1) {
				String objName = message.substring(message.indexOf("for ") + 4);
				objName = objName.substring(0, objName.indexOf(" "));
				final String className = objName;
				return new AbstractMap.SimpleEntry<>(objName, (cls) -> cls.getName().equals(className));
			} else if(message.indexOf("class ") != -1 && message.indexOf("package ") != -1 ){
				String className = message.substring(message.indexOf("class ")+6);
				className = className.substring(0, className.indexOf("\n"));
				String packageName = message.substring(message.indexOf("package") + 8);
				final String objName = packageName+"."+className;
				return new AbstractMap.SimpleEntry<>(objName, (cls) -> cls.getName().equals(objName));
			} else if(message.indexOf("symbol: class") != -1) {
				String className = message.substring(message.indexOf("class ")+6);
				final String objName = className;
				return new AbstractMap.SimpleEntry<>(objName, (cls) -> cls.getSimpleName().equals(objName));
			}
			return null;
		}
		
		private String getPackageNameFromErrorMessage(String message) {
			String objName = null;
			if (message.indexOf("package exists in another module") == -1 && message.indexOf("cannot be accessed from outside package") == -1) {
				if (message.indexOf("package ") != -1){
					objName = message.substring(message.indexOf("package") + 8);
					int firstOccOfSpaceIdx = objName.indexOf(" ");
					if(firstOccOfSpaceIdx!=-1) {
						objName = objName.substring(0, firstOccOfSpaceIdx);
					}
				}
			}
			return objName;
		}
		
	}
	
	public static class UnknownCompilerErrorMessageException extends RuntimeException {

		private static final long serialVersionUID = 1149980549799104408L;

		public UnknownCompilerErrorMessageException(String s) {
			super(s);
		}
	}		
	
	public static class MemorySource extends SimpleJavaFileObject implements Serializable {

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
	}
	
	public static class MemoryFileObject extends SimpleJavaFileObject implements Component {
		
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
	
	private static class Compilation {
		private static class Context implements Component {
			
			private Map<String, String> options;
			private Collection<MemorySource> sources;
			private ClassPathHunter classPathHunter;
			private Collection<SearchResult> classPathsSearchResults;
			private Collection<String> classRepositoriesPaths;
			private JavaMemoryCompiler javaMemoryCompiler;
			
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					options.put("-classpath", Optional.ofNullable(options.get("-classpath")).orElse("") + Paths.clean(path) + System.getProperty("path.separator"));
				}
			}
			
			private Context(
				JavaMemoryCompiler javaMemoryCompiler,
				ClassPathHunter classPathHunter,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				this.javaMemoryCompiler = javaMemoryCompiler;
				options =  new LinkedHashMap<>();
				this.sources = sources;
				this.classPathHunter = classPathHunter;
				if (classPaths != null) {
					for(String classPath : classPaths) {
						addToClassPath(classPath);
					}
				}
				this.classRepositoriesPaths = classRepositories;
				this.classPathsSearchResults = new LinkedHashSet<>();
			}
			
			private static Context create(
				JavaMemoryCompiler javaMemoryCompiler,
				ClassPathHunter classPathHunter,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				return new Context(javaMemoryCompiler, classPathHunter, sources, classPaths, classRepositories);
			}
			
			public Collection<FileSystemItem> findForPackageName(String packageName) throws Exception {
				FileSystemItem.CheckFile checkFileOptions = 
					FileSystemItem.CheckFile.forLabel(IterableObjectHelper.get(
						javaMemoryCompiler.config,
						Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
						JavaMemoryCompiler.Configuration.DEFAULT_VALUES
					)
				);
				SearchResult result = classPathHunter.findBy(
					SearchConfig.withoutUsingCache().addPaths(
						javaMemoryCompiler.compiledClassesClassPath.getAbsolutePath()
					).by(
						ClassCriteria.create().packageName((iteratedClassPackageName) ->
							Objects.equals(iteratedClassPackageName, packageName)
						)
					).checkFileOption(
						checkFileOptions
					).optimizePaths(
						true
					)
				);
				
				classPathsSearchResults.add(result);
				if (result.getClassPaths().isEmpty()) {
					result = classPathHunter.loadInCache(
						SearchConfig.forPaths(classRepositoriesPaths).by(
							ClassCriteria.create().packageName((iteratedClassPackageName) ->
								Objects.equals(iteratedClassPackageName, packageName)									
							)
						).checkFileOption(
							checkFileOptions
						).optimizePaths(
							true
						)
					).find();
					classPathsSearchResults.add(result);
				}
				return result.getClassPaths();
			}
			
			public Collection<FileSystemItem> findForClassName(Predicate<Class<?>> classPredicate) throws Exception {
				FileSystemItem.CheckFile checkFileOptions = 
					FileSystemItem.CheckFile.forLabel(IterableObjectHelper.get(
						javaMemoryCompiler.config,
						Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
						JavaMemoryCompiler.Configuration.DEFAULT_VALUES
					)
				);
				SearchResult result = classPathHunter.findBy(
					SearchConfig.withoutUsingCache().addPaths(javaMemoryCompiler.compiledClassesClassPath.getAbsolutePath()).by(
						ClassCriteria.create().allThat(classPredicate)
					).checkFileOption(
						checkFileOptions
					).optimizePaths(
						true
					)
				);
				classPathsSearchResults.add(result);
				if (result.getClassPaths().isEmpty()) {
					result = classPathHunter.loadInCache(
						SearchConfig.forPaths(classRepositoriesPaths).by(
							ClassCriteria.create().allThat(classPredicate)
						).checkFileOption(
							checkFileOptions
						).optimizePaths(
							true
						)
					).find();
					classPathsSearchResults.add(result);
				}
				return result.getClassPaths();
			}

			@Override
			public void close() {
				if (!classPathsSearchResults.isEmpty()) {
					classPathsSearchResults.stream().forEach(
						(result)  -> result.close()
					);
				}
				classPathsSearchResults = null;
				classPathHunter = null;
				options.clear();
				options = null;
				sources = null;		
			}

		}
	}
	
	@Override
	public void close() {
		compiler = null;
		classPathHunter = null;
	}
}