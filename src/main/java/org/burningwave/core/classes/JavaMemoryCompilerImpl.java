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
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;
import javax.tools.ToolProvider;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;

public class JavaMemoryCompilerImpl implements JavaMemoryCompiler, Component{
	private PathHelper pathHelper;
	ClassPathHelper classPathHelper;
	private JavaCompiler compiler;
	FileSystemItem compiledClassesRepository;
	
	JavaMemoryCompilerImpl(
		PathHelper pathHelper,
		ClassPathHelper classPathHelper
	) {
		this.pathHelper = pathHelper;
		this.classPathHelper = classPathHelper;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		this.compiledClassesRepository = FileSystemItem.of(((ClassPathHelperImpl)classPathHelper).getOrCreateTemporaryFolder("compiledClassesRepository"));
	}	
	
	@Override
	public 	ProducerTask<Compilation.Result> compile(Collection<String> sources) {
		return compile(sources, true);
	}
	
	@Override
	public 	ProducerTask<Compilation.Result> compile(Collection<String> sources, boolean storeCompiledClasses) {
		return compile(
			Compilation.Config.withSources(sources).setClassPaths(
				pathHelper.getAllMainClassPaths()
			).storeCompiledClasses(
				storeCompiledClasses
			)
		);
	}
	
	
	
	@Override
	public ProducerTask<Compilation.Result> compile(Compilation.Config config) {
		return compile(
			config.getSources(),
			getClassPathsFrom(config),
			getClassRepositoriesFrom(config),
			config.getCompiledClassesStorage()
		);
	}

	Collection<String> getClassRepositoriesFrom(Compilation.Config config) {
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

	Collection<String> getClassPathsFrom(Compilation.Config config) {
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
		String compiledClassesStorage
	) {	
		return BackgroundExecutor.createTask(() -> {
			logInfo("Try to compile: \n\n{}\n", String.join("\n", SourceCodeHandler.addLineCounter(sources)));
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
				String storedFilesClassPath = compiledClassesStorage != null ?
					compiledClassesRepository.getAbsolutePath() + "/" + compiledClassesStorage :
					null;
				if (!compiledFiles.isEmpty() && compiledClassesStorage != null ) {
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
				return new Compilation.Result(
					storedFilesClassPath  != null ? FileSystemItem.ofPath(storedFilesClassPath) : null, 
					compiledFiles, new HashSet<>(context.classPaths)
				);
			}
		}).submit();
	}	
	
	private void sourcesToMemorySources(Collection<String> sources, Collection<MemorySource> memorySources) {
		for (String source : sources) {
			String className = SourceCodeHandler.extractClassName(source);
			try {
				memorySources.add(new MemorySource(Kind.SOURCE, className, source));
			} catch (URISyntaxException eXC) {
				Throwables.throwException("Class name \"{}\" is not valid", className);
			}
		}
		
	}


	private Map<String, ByteBuffer> compile(Compilation.Context context) {
		if (!context.classPaths.isEmpty()) {
			logInfo("... Using class paths:\n\t{}",String.join("\n\t", context.classPaths));
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
	
	
	
	
	
	@Override
	public void close() {
		closeResources(() -> compiledClassesRepository == null, () -> {
			compiledClassesRepository.destroy();
			compiledClassesRepository = null;
			compiler = null;
			pathHelper = null;
		});
	}
}