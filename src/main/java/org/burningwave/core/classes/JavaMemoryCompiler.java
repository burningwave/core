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

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.classes.hunter.ClassPathHunter;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.classes.hunter.SearchResult;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;


public class JavaMemoryCompiler implements Component {
	
	private FileSystemHelper fileSystemHelper;
	private ClassHelper classHelper;
	private ClassPathHunter classPathHunter;
	private JavaCompiler compiler;
	private FileSystemItem compiledClassesClassPath;
	
	private JavaMemoryCompiler(
		FileSystemHelper fileSystemHelper,
		PathHelper pathHelper,
		ClassHelper classHelper,
		ClassPathHunter classPathHunter
	) {
		this.fileSystemHelper = fileSystemHelper;
		this.classPathHunter = classPathHunter;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.classHelper = classHelper;
		compiledClassesClassPath = FileSystemItem.ofPath(
			this.fileSystemHelper.createTemporaryFolder(getTemporaryFolderPrefix() + "_classPath").getAbsolutePath()
		);
	}	
	
	public static JavaMemoryCompiler create(
		FileSystemHelper fileSystemHelper,
		PathHelper pathHelper,
		ClassHelper classHelper,
		ClassPathHunter classPathHunter
	) {
		return new JavaMemoryCompiler(fileSystemHelper, pathHelper, classHelper, classPathHunter);
	}
	
	
	public Map<String, ByteBuffer> compile(
		Collection<String> sources, 
		Collection<String> classPaths, 
		Collection<String> classRepositoriesPaths) {
		Collection<JavaMemoryCompiler.MemorySource> memorySources = new ArrayList<>();
		sourcesToMemorySources(sources, memorySources);
		try (Compilation.Context context = Compilation.Context.create(this, classPathHunter, memorySources, new ArrayList<>(classPaths), new ArrayList<>(classRepositoriesPaths))) {
			Map<String, ByteBuffer> compiledFiles = _compile(context);
			if (!compiledFiles.isEmpty()) {
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
			String className = classHelper.extractClassName(source);
			try {
				memorySources.add(new MemorySource(Kind.SOURCE, className, source));
			} catch (URISyntaxException eXC) {
				throw Throwables.toRuntimeException("Class name \"" + className + "\" is not valid");
			}
		}
		
	}


	private Map<String, ByteBuffer> _compile(Compilation.Context context) {
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
			boolean done = task.call();
			if (!done) {
				return _compile(context);
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
			Collection<File> fsObjects = null;
			
			Map.Entry<String, Predicate<Class<?>>> classNameAndClassPredicate = getClassPredicateFromErrorMessage(message);
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
				throw new UnknownCompilerErrorMessageException("Can't retrieve class or package from message:\n" + message);
			}
			if (fsObjects == null || fsObjects.isEmpty()) {
				throw Throwables.toRuntimeException("Class or package \"" + classNameAndClassPredicate.getKey() + "\" not found");
			}
			fsObjects.forEach((fsObject) -> context.addToClassPath(fsObject.getAbsolutePath()));		
		}

		private Map.Entry<String, Predicate<Class<?>>> getClassPredicateFromErrorMessage(String message) {
			if (message.contains("class file for") && message.contains("not found")) {
				String objName = message.substring(message.indexOf("for ") + 4);
				objName = objName.substring(0, objName.indexOf(" "));
				final String className = objName;
				return new AbstractMap.SimpleEntry<>(objName, (cls) -> cls.getName().equals(className));
			} else if(message.contains("class") && message.contains("package")){
				String className = message.substring(message.indexOf("class ")+6);
				className = className.substring(0, className.indexOf("\n"));
				String packageName = message.substring(message.indexOf("package") + 8);
				final String objName = packageName+"."+className;
				return new AbstractMap.SimpleEntry<>(objName, (cls) -> cls.getName().equals(objName));
			} else if(message.contains("symbol: class")) {
				String className = message.substring(message.indexOf("class ")+6);
				final String objName = className;
				return new AbstractMap.SimpleEntry<>(objName, (cls) -> cls.getSimpleName().equals(objName));
			}
			return null;
		}
		
		private String getPackageNameFromErrorMessage(String message) {
			String objName = null;
			if (!message.contains("package exists in another module")) {
				if (message.contains("package")){
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
			private Collection<SearchResult<Class<?>, File>> classPathsSearchResults;
			private Collection<String> classRepositoriesPaths;
			private JavaMemoryCompiler javaMemoryCompiler;
			
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					options.put("-classpath", Optional.ofNullable(options.get("-classpath")).orElse("") + Strings.Paths.clean(path) + System.getProperty("path.separator"));
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
			
			public Collection<File> findForPackageName(String packageName) throws Exception {
				SearchResult<Class<?>, File> result = classPathHunter.findBy(
					SearchConfig.forPaths(javaMemoryCompiler.compiledClassesClassPath.getAbsolutePath()).by(
						ClassCriteria.create().packageName((iteratedClassPackageName) ->
							iteratedClassPackageName.equals(packageName)
						)
					)
				);
				classPathsSearchResults.add(result);
				if (result.getItemsFound().isEmpty()) {
					result = classPathHunter.findBy(
						SearchConfig.forPaths(classRepositoriesPaths).by(
							ClassCriteria.create().packageName((iteratedClassPackageName) ->
								iteratedClassPackageName.equals(packageName)
							)
						)
					);
					classPathsSearchResults.add(result);
				}
				return result.getItemsFound();
			}
			
			public Collection<File> findForClassName(Predicate<Class<?>> classPredicate) throws Exception {
				SearchResult<Class<?>, File> result = classPathHunter.findBy(
					SearchConfig.forPaths(javaMemoryCompiler.compiledClassesClassPath.getAbsolutePath()).by(
						ClassCriteria.create().allThat(classPredicate)
					)
				);
				classPathsSearchResults.add(result);
				if (result.getItemsFound().isEmpty()) {
					result = classPathHunter.findBy(
						SearchConfig.forPaths(javaMemoryCompiler.compiledClassesClassPath.getAbsolutePath()).by(
							ClassCriteria.create().allThat(classPredicate)
						)
					);
					classPathsSearchResults.add(result);
				}
				return result.getItemsFound();
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
		classHelper = null;
	}
}