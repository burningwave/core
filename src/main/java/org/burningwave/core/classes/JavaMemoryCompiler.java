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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.burningwave.core.Closeable;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public interface JavaMemoryCompiler {
	
	public static class Configuration {
		
		public static class Key {
			public static final String CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.class-paths";
			public static final String BLACK_LISTED_CLASS_PATHS =  PathHelper.Configuration.Key.PATHS_PREFIX + "java-memory-compiler.black-listed-class-paths";
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
			defaultValues.put(
				Key.BLACK_LISTED_CLASS_PATHS,
				"//${paths.main-class-paths}/..//children:.*?surefirebooter\\d{0,}\\.jar;"
			);			
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static JavaMemoryCompiler create(
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Properties config
	) {
		return new JavaMemoryCompilerImpl(pathHelper, classPathHelper, config);
	}

	public ProducerTask<Compilation.Result> compile(Compilation.Config config);	
	
	public static class Compilation {
		
		public static class Config {
			private Collection<String> sources;
			
			private Collection<String> classPaths;
			private Collection<String> additionalClassPaths;
			
			private Collection<String> blackListedClassPaths;
			private Collection<String> additionalBlackListedClassPaths;
			
			private Collection<String> classRepositories;
			private Collection<String> additionalClassRepositories;
			
			private String compiledClassesStorage;
			private boolean useTemporaryFolderForStoring;
			
			private Config() {
				this.sources = new HashSet<>();
				storeCompiledClassesToTemporaryFolder("common");
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
			
			@SafeVarargs
			public final static Config forUnitSourceGenerator(UnitSourceGenerator... sources) {
				return forUnitSourceGenerators(Arrays.asList(sources));
			}
			
			@SafeVarargs
			public final static Config forUnitSourceGenerators(Collection<UnitSourceGenerator>... sourceCollections) {
				Config compileConfig = new Config();
				for (Collection<UnitSourceGenerator> sourceCollection : sourceCollections) {
					compileConfig.sources.addAll(sourceCollection.stream().map(source -> source.make()).collect(Collectors.toList()));
				}
				return compileConfig;
			}
			
			public Config storeCompiledClasses(boolean flag) {
				if (flag) {
					if (compiledClassesStorage == null) {
						storeCompiledClassesToTemporaryFolder("common");
					}
				} else {
					compiledClassesStorage = null;
				}
				return this;
			}
			
			public Config storeCompiledClassesToTemporaryFolder(String folderName) {
				compiledClassesStorage = folderName;
				useTemporaryFolderForStoring = true;
				return this;
			}
			
			public Config storeCompiledClassesTo(String folderName) {
				compiledClassesStorage = folderName;
				useTemporaryFolderForStoring = false;
				return this;
			}
			
			public Config storeCompiledClassesToNewTemporaryFolder() {
				return storeCompiledClassesToTemporaryFolder(UUID.randomUUID().toString());
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
			
		////////////////////	
			
			@SafeVarargs
			public final Config setBlackListedClassPaths(Collection<String>... classPathCollections) {
				if (blackListedClassPaths == null) {
					blackListedClassPaths = new HashSet<>();
				}
				for (Collection<String> classPathCollection : classPathCollections) {
					blackListedClassPaths.addAll(classPathCollection);
				}
				return this;
			}
			
			@SafeVarargs
			public final Config setBlackListedClassPaths(String... classPaths) {
				return setBlackListedClassPaths(Arrays.asList(classPaths));
			}

		////////////////////	
			
			@SafeVarargs
			public final Config addBlackListedClassPaths(Collection<String>... classPathCollections) {
				if (additionalBlackListedClassPaths == null) {
					additionalBlackListedClassPaths = new HashSet<>();
				}
				for (Collection<String> classPathCollection : classPathCollections) {
					additionalBlackListedClassPaths.addAll(classPathCollection);
				}
				return this;
			}
			
			@SafeVarargs
			public final Config addBlackListedClassPaths(String... classPaths) {
				return addBlackListedClassPaths(Arrays.asList(classPaths));
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
			
			Collection<String> getBlackListedClassPaths() {
				return blackListedClassPaths;
			}

			Collection<String> getAdditionalBlackListedClassPaths() {
				return additionalBlackListedClassPaths;
			}
			
			boolean isStoringCompiledClassesEnabled() {
				return compiledClassesStorage != null;
			}
			
			String getCompiledClassesStorage() {
				return compiledClassesStorage;
			}

			boolean useTemporaryFolderForStoring() {
				return useTemporaryFolderForStoring;
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
		
		public static class Exception extends RuntimeException {

			private static final long serialVersionUID = 4515340268068466479L;

			public Exception(String s) {
				super(s);
			}
			
			public Exception(String s, Throwable cause) {
				super(s, cause);
			}
		}	
	}
}