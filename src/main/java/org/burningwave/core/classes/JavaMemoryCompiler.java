package org.burningwave.core.classes;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.classes.hunter.ClassPathHunter;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.classes.hunter.SearchResult;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.PathHelper;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;


public class JavaMemoryCompiler implements Component {
	private ClassHelper classHelper;
	private ClassPathHunter classPathHunter;
	private JavaCompiler compiler;
	private JavaMemoryCompiler.MemoryFileManager memoryFileManager;
	
	private JavaMemoryCompiler(
		PathHelper pathHelper,
		ClassHelper classHelper,
		ClassPathHunter classPathHunter) {
		this.classPathHunter = classPathHunter;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.memoryFileManager = new MemoryFileManager(compiler);
		this.classHelper = classHelper;
	}
	
	
	public static JavaMemoryCompiler create(
			PathHelper pathHelper,
			ClassHelper classHelper,
			ClassPathHunter classPathHunter) {
		return new JavaMemoryCompiler(pathHelper, classHelper, classPathHunter);
	}
	
	
	public Collection<MemoryFileObject> compile(
		Collection<String> sources, 
		Collection<String> classPaths, 
		Collection<String> classRepositoriesPaths) {
		Collection<JavaMemoryCompiler.MemorySource> memorySources = new ArrayList<>();
		sourcesToMemorySources(sources, memorySources);
		try (Compilation.Context context = Compilation.Context.create(classPathHunter, memorySources, new ArrayList<>(classPaths), new ArrayList<>(classRepositoriesPaths))) {
			return _compile(context);	
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


	private Collection<MemoryFileObject> _compile(Compilation.Context context) {
		List<String> options = new ArrayList<String>();
		if (!context.options.isEmpty()) {
			context.options.forEach((key, val) -> {
				options.add(key);
				Optional.ofNullable(val).ifPresent(value -> {
					options.add(value);
				});
				
			});
		}
		memoryFileManager.getCompilationUnits().addAll(context.sources);
		Set<MemoryFileObject> alreadyCompiledClass = new LinkedHashSet<>(memoryFileManager.getCompiledFiles());		
		CompilationTask task = compiler.getTask(null, memoryFileManager,
				new MemoryDiagnosticListener(context), options, null, memoryFileManager.getCompilationUnits());
		boolean done = task.call();
		if (!done) {
			return _compile(context);
		} else {
			Set<MemoryFileObject> compiledFiles = new LinkedHashSet<>(memoryFileManager.getCompiledFiles());
			for (MemoryFileObject memoryFileObject: alreadyCompiledClass) {
				compiledFiles.removeIf((memFileObj) -> memFileObj.getName().equals(memoryFileObject.getName()));
			}
			return compiledFiles;
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
			String classOrPackageName = getClassNameFromErrorMessage(message);
			if (Strings.isNotEmpty(classOrPackageName)) {
				try {
					fsObjects = context.findForClassName(classOrPackageName);
				} catch (Exception e) {
					logError("Exception occurred", e);
				}
			} else if (Strings.isNotEmpty(classOrPackageName = getPackageNameFromErrorMessage(message))) {			
				try {
					fsObjects = context.findForPackageName(classOrPackageName);
				} catch (Exception e) {
					logError("Exception occurred", e);
				}
			} else {
				throw new UnknownCompilerErrorMessageException("Can't retrieve class or package from message:\n" + message);
			}
			if (fsObjects == null || fsObjects.isEmpty()) {
				throw Throwables.toRuntimeException("Class or package \"" + classOrPackageName + "\" not found");
			}
			fsObjects.forEach((fsObject) -> context.addToClassPath(fsObject.getAbsolutePath()));		
		}

		private String getClassNameFromErrorMessage(String message) {
			String objName = null;
			if (message.contains("class file for") && message.contains("not found")) {
				objName = message.substring(message.indexOf("for ") + 4);
				objName = objName.substring(0, objName.indexOf(" "));
			} else if(message.contains("class") && message.contains("package")){
				String className = message.substring(message.indexOf("class ")+6);
				className = className.substring(0, className.indexOf("\n"));
				String packageName = message.substring(message.indexOf("package") + 8);
				objName = packageName+"."+className;
			}
			return objName;
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
	    	return baos.getBuffer();
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
		
		private List<JavaMemoryCompiler.MemorySource> compilationUnits;
		private List<MemoryFileObject> compiledFiles;
				
		MemoryFileManager(JavaCompiler compiler) {
	        super(compiler.getStandardFileManager(null, null, null));
	        compilationUnits = new CopyOnWriteArrayList<>();
	        compiledFiles = new CopyOnWriteArrayList<>();
	    }

		
		void reset() {
			compiledFiles.clear();
			compilationUnits.clear();
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
		
		
		public List<JavaMemoryCompiler.MemorySource> getCompilationUnits() {
			return compilationUnits;
		}
		
		@Override
		public void close() {
			compilationUnits.clear();
			compiledFiles.forEach(compiledFile -> compiledFile.close());
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
			
			
			void addToClassPath(String path) {
				if (Strings.isNotBlank(path)) {
					options.put("-classpath", Optional.ofNullable(options.get("-classpath")).orElse("") + Strings.Paths.clean(path) + System.getProperty("path.separator"));
				}
			}
			
			private Context(
				ClassPathHunter classPathHunter,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
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
				ClassPathHunter classPathHunter,
				Collection<MemorySource> sources,
				Collection<String> classPaths,
				Collection<String> classRepositories
			) {
				return new Context(classPathHunter, sources, classPaths, classRepositories);
			}
			
			public Collection<File> findForPackageName(String packageName) throws Exception {
				SearchResult<Class<?>, File> result = classPathHunter.findBy(
					SearchConfig.forPaths(classRepositoriesPaths).by(
						ClassCriteria.create().packageName((iteratedClassPackageName) ->
							iteratedClassPackageName.equals(packageName)
						)
					)
				);
				classPathsSearchResults.add(result);
				return result.getItemsFound();
			}
			
			public Collection<File> findForClassName(String className) throws Exception {
				SearchResult<Class<?>, File> result = classPathHunter.findBy(
					SearchConfig.forPaths(classRepositoriesPaths).by(
						ClassCriteria.create().className((iteratedClassName) -> 
							iteratedClassName.equals(className))
					)
				);
				classPathsSearchResults.add(result);
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
		ThrowingRunnable.run(() -> {
			memoryFileManager.close();
			memoryFileManager = null;
		});
		compiler = null;
		classPathHunter = null;
		classHelper = null;
	}
}