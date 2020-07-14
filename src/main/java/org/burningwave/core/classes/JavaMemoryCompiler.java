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

import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;
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
			public static final String CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-paths";
			public static final String ADDITIONAL_CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.additional-class-paths";
			public static final String CLASS_REPOSITORIES =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-repositories";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(
				Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScannerAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			DEFAULT_VALUES.put(
				Key.CLASS_PATHS,
				PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + PathHelper.Configuration.Key.PATHS_SEPARATOR + 
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_PATHS_EXTENSION + "}" + PathHelper.Configuration.Key.PATHS_SEPARATOR + 
				"${" + Configuration.Key.ADDITIONAL_CLASS_PATHS + "}"
			);
		}
	}
	
	private PathHelper pathHelper;
	private ClassPathHunter classPathHunter;
	private JavaCompiler compiler;
	private FileSystemItem compiledClassesRepository;
	private FileSystemItem basePathForLibCopies;
	private FileSystemItem basePathForClassCopies;
	private Properties config;	
	
	private JavaMemoryCompiler(
		PathHelper pathHelper,
		ClassPathHunter classPathHunter,
		Properties config
	) {
		this.pathHelper = pathHelper;
		this.classPathHunter = classPathHunter;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.compiledClassesRepository = FileSystemItem.of(getOrCreateTemporaryFolder("compiledClassesRepository"));
		this.basePathForLibCopies = FileSystemItem.of(getOrCreateTemporaryFolder("lib"));
		this.basePathForClassCopies = FileSystemItem.of(getOrCreateTemporaryFolder("classes"));
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
	
	public 	CompilationResult compile(Collection<String> sources) {
		return compile(sources, true);
	}
	
	public 	CompilationResult compile(Collection<String> sources, boolean storeCompiledClasses) {
		return compile(
			CompileConfig.withSources(sources).setClassPaths(
				pathHelper.getAllMainClassPaths()
			).setClassRepositoriesWhereToSearchNotFoundClasses(
				pathHelper.getPaths(JavaMemoryCompiler.Configuration.Key.CLASS_REPOSITORIES)
			).storeCompiledClasses(
				storeCompiledClasses
			)
		);
	}
	
	public CompilationResult compile(CompileConfig config) {
		return compile(
			config.getSources(),
			IterableObjectHelper.merge(
				config::getClassPaths,
				config::getAdditionalClassPaths,
				() -> 
					pathHelper.getPaths(
						Configuration.Key.CLASS_PATHS
					)
			),
			IterableObjectHelper.merge(
				config::getClassRepositoriesWhereToSearchNotFoundClasses,
				config::getAdditionalRepositoriesWhereToSearchNotFoundClasses,
				() -> {
					Collection<String> classRepositories = pathHelper.getPaths(
						Configuration.Key.CLASS_REPOSITORIES
					);
					if (!classRepositories.isEmpty()) {
						config.neededClassesPreventiveSearch(true);
					}
					return classRepositories;
				}
			),
			config.isNeededClassesPreventiveSearchEnabled(),
			config.isStoringCompiledClassesEnabled()
		);
	}
	
	private CompilationResult compile(
		Collection<String> sources, 
		Collection<String> classPaths, 
		Collection<String> classRepositoriesPaths,
		boolean neededClassesPreventiveSearchEnabled,
		boolean storeCompiledClasses
	) {	
		logInfo("Try to compile: \n\n{}\n",String.join("\n", sources));
		if (neededClassesPreventiveSearchEnabled) {
			classPaths = computeClassPaths(classRepositoriesPaths, sources, null);
		}
		Collection<JavaMemoryCompiler.MemorySource> memorySources = new ArrayList<>();
		sourcesToMemorySources(sources, memorySources);
		try (Compilation.Context context = Compilation.Context.create(
				this,
				memorySources, 
				new ArrayList<>(classPaths), 
				new ArrayList<>(classRepositoriesPaths)
			)
		) {
			Map<String, ByteBuffer> compiledFiles = _compile(context, null);
			if (!compiledFiles.isEmpty() && storeCompiledClasses) {
				compiledFiles.forEach((className, byteCode) -> {
					JavaClass javaClass = JavaClass.create(byteCode);
					javaClass.storeToClassPath(compiledClassesRepository.getAbsolutePath() + "/" + UUID.randomUUID().toString());
				});
			}			
			return new CompilationResult(compiledClassesRepository, compiledFiles);
		}
	}
	
	Collection<String> computeClassPaths(
		Collection<String> classRepositories,
		Collection<String> sources,
		ClassCriteria otherClassCriteria
	) {
		Collection<String> imports = new HashSet<>();
		for (String sourceCode : sources) {
			imports.addAll(SourceCodeHandler.extractImports(sourceCode));
		}	
		Collection<String> classPaths = new HashSet<>();
		Collection<String> toBeAdjuested = new HashSet<>(); 
		for (String classPath : classRepositories) {
			FileSystemItem fIS = FileSystemItem.ofPath(classPath);
			if (fIS.exists()) {
				if (fIS.isArchive()) {
					toBeAdjuested.add(fIS.getAbsolutePath());
				} else {
					classPaths.add(fIS.getAbsolutePath());
				}
			}
		}
		if (!toBeAdjuested.isEmpty()) {
			FileSystemItem.CheckingOption checkFileOption = 
				FileSystemItem.CheckingOption.forLabel(config.resolveStringValue(
					Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
					JavaMemoryCompiler.Configuration.DEFAULT_VALUES
				)
			);
			ClassCriteria classCriteria = ClassCriteria.create().className(
				className -> 
					imports.contains(className)
			);
			if (otherClassCriteria != null) {
				classCriteria = classCriteria.or(otherClassCriteria);
			}
			try(SearchResult result = classPathHunter.loadInCache(
					SearchConfig.forPaths(toBeAdjuested).withScanFileCriteria(
						FileSystemItem.Criteria.forClassTypeFiles(checkFileOption)
					).by(
						classCriteria
					).optimizePaths(
						true
					)
				).find()
			) {	
				Collection<FileSystemItem> effectiveClassPaths = result.getClassPaths();
				if (!effectiveClassPaths.isEmpty()) {
					for (FileSystemItem fsObject : effectiveClassPaths) {
						if (fsObject.isCompressed()) {					
							ThrowingRunnable.run(() -> {
								synchronized (this) {
									FileSystemItem classPathBasePath = fsObject.isArchive() ?
										basePathForLibCopies :
										basePathForClassCopies
									;
									FileSystemItem classPath = FileSystemItem.ofPath(
										classPathBasePath.getAbsolutePath() + "/" + fsObject.getName()
									);
									if (!classPath.refresh().exists()) {
										fsObject.copyTo(classPathBasePath.getAbsolutePath());
									}
									classPaths.add(
										classPath.getAbsolutePath()
									);
									//Free memory
									classPath.reset();
								}
							});
						} else {
							classPaths.add(fsObject.getAbsolutePath());
						}
					}
				}
			}
		}
		return classPaths;
	}
	
	Collection<String> searchClassPathsInCompiledClassesRepositoryWithoutTheUseOfCache(ClassCriteria classCriteria) {
		FileSystemItem.CheckingOption checkFileOption = 
			FileSystemItem.CheckingOption.forLabel(config.resolveStringValue(
				Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				JavaMemoryCompiler.Configuration.DEFAULT_VALUES
			)
		);
		Collection<String> classPaths = new HashSet<>();
		try (SearchResult result = classPathHunter.findBy(
				SearchConfig.withoutUsingCache().addPaths(compiledClassesRepository.getAbsolutePath()).by(
					classCriteria
				).withScanFileCriteria(
					FileSystemItem.Criteria.forClassTypeFiles(checkFileOption)
				).optimizePaths(
					true
				)
			)
		) {	
			for (FileSystemItem classPath : result.getClassPaths()) {
				classPaths.add(classPath.getAbsolutePath());
				classPath.reset();
			}
		}
		return classPaths;
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
				new DiagnosticListener(context), options, null,
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
	
	static class DiagnosticListener implements javax.tools.DiagnosticListener<JavaFileObject>, Serializable, Component {

		private static final long serialVersionUID = 4404913684967693355L;
		
		private Compilation.Context context;
		
		DiagnosticListener (Compilation.Context context) {
			this.context = context;
		}
		
		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			String message = diagnostic.getMessage(Locale.ENGLISH);
			if (message.contains("unchecked or unsafe operations") || message.contains("Recompile with -Xlint:unchecked")) {
				context.options.put("-Xlint:", "unchecked");
				return;
			}
			Collection<String> fsObjects = null;
			
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
				throw Throwables.toRuntimeException(
					Optional.ofNullable(classNameAndClassPredicate).map(bag -> "Class or package \"" + bag.getKey() + "\" not found").orElseGet(() -> message)
				);
			}
			fsObjects.forEach((fsObject) -> {
				context.addToClassPath(fsObject);
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
	    
	    public String getContent() {
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
			private Collection<String> classRepositoriesPaths;
			private JavaMemoryCompiler javaMemoryCompiler;
			
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					options.put("-classpath", Optional.ofNullable(options.get("-classpath")).orElse("") + Paths.clean(path) + System.getProperty("path.separator"));
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
				this.sources = sources;
				if (classPaths != null) {
					for(String classPath : classPaths) {
						addToClassPath(classPath);
					}
				}
				this.classRepositoriesPaths = classRepositories;
			}
			
			private static Context create(
				JavaMemoryCompiler javaMemoryCompiler,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				return new Context(javaMemoryCompiler, sources, classPaths, classRepositories);
			}
			
			public Collection<String> findForPackageName(String packageName) throws Exception {
				Collection<String> classPaths = javaMemoryCompiler.searchClassPathsInCompiledClassesRepositoryWithoutTheUseOfCache(
					ClassCriteria.create().packageName((iteratedClassPackageName) ->
							Objects.equals(iteratedClassPackageName, packageName)
						)
				);
				if (classPaths.isEmpty()) {
					classPaths.addAll(
						javaMemoryCompiler.computeClassPaths(
							classRepositoriesPaths, 
							sources.stream().map(ms -> ms.getContent()).collect(Collectors.toCollection(HashSet::new)), 
							ClassCriteria.create().packageName((iteratedClassPackageName) ->
								Objects.equals(iteratedClassPackageName, packageName)									
							)
						)
					);
				}
				return classPaths;
			}
			
			public Collection<String> findForClassName(Predicate<Class<?>> classPredicate) throws Exception {
				Collection<String> classPaths = javaMemoryCompiler.searchClassPathsInCompiledClassesRepositoryWithoutTheUseOfCache(
					ClassCriteria.create().allThat(classPredicate)
				);
				if (classPaths.isEmpty()) {
					classPaths.addAll(javaMemoryCompiler.computeClassPaths(
						classRepositoriesPaths, 
						sources.stream().map(ms -> ms.getContent()).collect(Collectors.toCollection(HashSet::new)), 
						ClassCriteria.create().allThat(classPredicate))
					);
				}
				return classPaths;
			}

			@Override
			public void close() {
				options.clear();
				options = null;
				sources = null;
				classRepositoriesPaths.clear();
				classRepositoriesPaths = null;
				javaMemoryCompiler = null;
			}

		}
	}
	
	@Override
	public void close() {
		unregister(config);
		FileSystemHelper.delete(compiledClassesRepository.getAbsolutePath());
		compiledClassesRepository.destroy();
		FileSystemHelper.delete(basePathForLibCopies.getAbsolutePath());
		basePathForLibCopies.destroy();
		FileSystemHelper.delete(basePathForClassCopies.getAbsolutePath());
		basePathForClassCopies.destroy();
		compiler = null;
		classPathHunter = null;
		config = null;
	}
	
	
	public static class CompilationResult {
		private FileSystemItem classPath;
		private Map<String, ByteBuffer> compiledFiles;
		
		
		private CompilationResult (FileSystemItem classPath, Map<String, ByteBuffer> compiledFiles) {
			this.classPath = classPath;
			this.compiledFiles = compiledFiles;
		}


		public FileSystemItem getClassPath() {
			return classPath;
		}


		public Map<String, ByteBuffer> getCompiledFiles() {
			return compiledFiles;
		}	
		
	}
}