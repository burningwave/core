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

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Closeable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.JavaMemoryCompiler.Compilation;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public interface ClassFactory {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String DEFAULT_CLASS_LOADER = "class-factory.default-class-loader";

			public static final String CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER = PathHelper.Configuration.Key.PATHS_PREFIX + "class-factory.default-class-loader.class-repositories";
			public static final String ADDITIONAL_CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER =
				PathHelper.Configuration.Key.PATHS_PREFIX + "class-factory.default-class-loader." + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_KEY + ".additional-class-repositories";
			public static final String BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-factory.byte-code-hunter.search-config.check-file-option";
					
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
	
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			//DEFAULT_VALUES.put(Key.DEFAULT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
			defaultValues.put(Configuration.Key.DEFAULT_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.DEFAULT_CLASS_LOADER + "." + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_KEY + ".additional-imports}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR + 
				PathScannerClassLoader.class.getName() + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR
			);
			defaultValues.put(Configuration.Key.DEFAULT_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX, ClassFactory.class.getPackage().getName() + ".DefaultClassLoaderRetrieverForClassFactory");
			//DEFAULT_VALUES.put(Key.DEFAULT_CLASS_LOADER, "(Supplier<ClassLoader>)() -> ((ComponentSupplier)parameter[0]).getClassHunter().getPathScannerClassLoader()");
			defaultValues.put(
				Key.DEFAULT_CLASS_LOADER,
				(Function<ComponentSupplier, ClassLoader>)(componentSupplier) ->
					componentSupplier.getPathScannerClassLoader()
			);
			defaultValues.put(
				Key.CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER,
				"${" + JavaMemoryCompiler.Configuration.Key.CLASS_PATHS + "}" + PathHelper.Configuration.getPathsSeparator() + 
				"${" + JavaMemoryCompiler.Configuration.Key.CLASS_REPOSITORIES + "}" + PathHelper.Configuration.getPathsSeparator() + 
				"${" + Key.ADDITIONAL_CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER + "}"				
			);			
			defaultValues.put(
				Key.BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScanner.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static ClassFactory create(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Object defaultClassLoaderSupplier,
		Properties config
	) {
		return new ClassFactoryImpl(
			byteCodeHunter,
			classPathHunterSupplier,
			javaMemoryCompiler, 
			pathHelper,
			classPathHelper,
			defaultClassLoaderSupplier,
			config
		);
	}
	
	public ClassRetriever loadOrBuildAndDefine(UnitSourceGenerator... unitsCode);

	public <L extends LoadOrBuildAndDefineConfigAbst<L>> ClassRetriever loadOrBuildAndDefine(L config);

	public void closeClassRetrievers();

	public void reset(boolean closeClassRetrievers);
	
	public static class ClassRetriever implements Closeable, ManagedLogger {
		ClassLoader classLoader;
		ClassFactory classFactory;
		Supplier<Compilation.Config> compilationConfigSupplier;
		Compilation.Config compilationConfig;
		AtomicReference<Map<String, ByteBuffer>> byteCodesWrapper;
		Collection<String> uSGClassNames;
		boolean compilationClassPathHasBeenAdded;
		boolean isItPossibleToAddClassPaths;
		Collection<String> classesSearchedInAdditionalClassRepositoriesForClassLoader;
		Collection<String> classesSearchedInCompilationDependenciesPaths;
		Collection<String> additionalClassRepositoriesForClassLoader;
		ProducerTask<Compilation.Result> compilationTask;
		boolean useOneShotJavaCompiler;
		ClassPathHelper classPathHelper;
		JavaMemoryCompiler compiler;
		
		ClassRetriever (
			ClassFactory classFactory,
			Function<ClassRetriever, ClassLoader> classLoaderSupplier,
			Supplier<Compilation.Config> compileConfigSupplier,
			boolean useOneShotJavaCompiler,
			Collection<String> additionalClassRepositoriesForClassLoader,
			Collection<String> uSGClassNames
		) {
			this.classLoader = classLoaderSupplier.apply(this);
			this.classFactory = classFactory;
			((ClassFactoryImpl)this.classFactory).register(this);
			this.byteCodesWrapper = new AtomicReference<>();
			this.isItPossibleToAddClassPaths = ClassLoaders.isItPossibleToAddClassPaths(classLoader);
			this.classesSearchedInAdditionalClassRepositoriesForClassLoader = new HashSet<>();
			this.classesSearchedInCompilationDependenciesPaths = new HashSet<>();
			this.additionalClassRepositoriesForClassLoader = additionalClassRepositoriesForClassLoader;
			this.uSGClassNames = uSGClassNames;
			this.compilationConfigSupplier = compileConfigSupplier;
			this.useOneShotJavaCompiler = useOneShotJavaCompiler;
		}
		
		public Class<?> get(String className) {
			try {
				try {
					try {
						try {
							try {
								try {
									return classLoader.loadClass(className);
								} catch (ClassNotFoundException | NoClassDefFoundError exc) {
									if (!isItPossibleToAddClassPaths || compilationClassPathHasBeenAdded || !getCompilationConfig().isStoringCompiledClassesEnabled()) {
										throw exc;
									}
									Compilation.Result compilationResult = getCompilationResult();
									compilationClassPathHasBeenAdded = true;
									ClassLoaders.addClassPath(
										classLoader,
										compilationResult.getClassPath().getAbsolutePath()::equals,
										compilationResult.getClassPath().getAbsolutePath()
									);
									return get(className);
								}								
							} catch (ClassNotFoundException | NoClassDefFoundError exc) {
								Compilation.Result compilationResult = getCompilationResult();
								Map<String, ByteBuffer> compiledClasses = new HashMap<>(compilationResult.getCompiledFiles());
								if (compiledClasses.containsKey(className)) {
									return ClassLoaders.loadOrDefineByByteCode(className, compiledClasses, classLoader);
								}
								throw exc;
							}
						} catch (ClassNotFoundException | NoClassDefFoundError exc) {
							if (!isItPossibleToAddClassPaths) {
								throw exc;
							}
							Collection<String> notFoundClasses = Classes.retrieveNames(exc);
							if (classesSearchedInAdditionalClassRepositoriesForClassLoader.containsAll(notFoundClasses)) {
								throw exc;
							}
							Collection<String> whereToFind = new HashSet<>(additionalClassRepositoriesForClassLoader);
							String absolutePathOfCompiledFilesClassPath = getCompilationResult().getClassPath().getAbsolutePath();
							whereToFind.add(absolutePathOfCompiledFilesClassPath);
							classesSearchedInAdditionalClassRepositoriesForClassLoader.addAll(notFoundClasses);
							if (!classPathHelper.computeAndAddAllToClassLoader(
								classLoader, whereToFind, 
								Arrays.asList(absolutePathOfCompiledFilesClassPath),
								className,
								notFoundClasses
							).isEmpty()) {
								return get(className);
							}
							throw exc;
						}
					} catch (ClassNotFoundException | NoClassDefFoundError exc) {
						if (!isItPossibleToAddClassPaths) {
							throw exc;
						}
						Collection<String> notFoundClasses = Classes.retrieveNames(exc);
						if (classesSearchedInCompilationDependenciesPaths.containsAll(notFoundClasses)) {
							throw exc;
						}
						Compilation.Result compilationResult = getCompilationResult();
						Collection<String> classPaths = new HashSet<>(compilationResult.getDependencies());
						Collection<String> classPathsToBeRefreshed = new HashSet<>();
						if (getCompilationConfig().isStoringCompiledClassesEnabled()) {
							String compilationResultAbsolutePath = compilationResult.getClassPath().getAbsolutePath();
							classPaths.add(compilationResultAbsolutePath);
							classPathsToBeRefreshed.add(compilationResultAbsolutePath);
						}										
						classesSearchedInCompilationDependenciesPaths.addAll(notFoundClasses);
						if (!classPathHelper.computeAndAddAllToClassLoader(
							classLoader, classPaths, classPathsToBeRefreshed, className, notFoundClasses
						).isEmpty()) {
							return get(className);
						}
						throw exc;
					}
				} catch (ClassNotFoundException | NoClassDefFoundError | Classes.Loaders.UnsupportedException exc) {
					return ClassLoaders.loadOrDefineByByteCode(className, 
						loadBytecodesFromClassPaths(
							this.byteCodesWrapper,
							getCompilationResult().getCompiledFiles(),
							additionalClassRepositoriesForClassLoader
						).get(), classLoader
					);
				}
			} catch (ClassNotFoundException | NoClassDefFoundError exc) {
				return Executor.get(() -> {
					return ClassLoaders.loadOrDefineByByteCode(className, 
						loadBytecodesFromClassPaths(
							this.byteCodesWrapper,
							getCompilationResult().getCompiledFiles(),
							additionalClassRepositoriesForClassLoader,
							getCompilationResult().getDependencies()
						).get(), classLoader
					);
				});
			}
		}

		private ProducerTask<Compilation.Result> getCompilationTask() {
			if (this.compilationTask == null) {
				synchronized (compilationConfigSupplier) {
					if (this.compilationTask == null) {
						classPathHelper = !useOneShotJavaCompiler ? ((ClassFactoryImpl)this.classFactory).classPathHelper : ClassPathHelper.create(
							((ClassFactoryImpl)this.classFactory).getClassPathHunter(),
							((ClassFactoryImpl)this.classFactory).config
						);
					
						compiler = !useOneShotJavaCompiler ?
							((ClassFactoryImpl)this.classFactory).javaMemoryCompiler :
							JavaMemoryCompiler.create(
								((ClassFactoryImpl)this.classFactory).pathHelper,
								classPathHelper,
								((ClassFactoryImpl)this.classFactory).config
							);
						this.compilationTask = compiler.compile(getCompilationConfig());
					}
				}
			}
			return this.compilationTask;
		}
		
		private Compilation.Result getCompilationResult() {
			Compilation.Result compilationResult = getCompilationTask().join();
			if (getCompilationTask().getException() != null) {
				Throwables.throwException(getCompilationTask().getException());
			}
			return compilationResult;
		}

		private Compilation.Config getCompilationConfig() {
			if (compilationConfig == null) {
				synchronized (compilationConfigSupplier) {
					if (compilationConfig == null) {
						compilationConfig = compilationConfigSupplier.get();
					}
				}
			}
			return compilationConfig;
			
		}
		
		@SafeVarargs
		private final AtomicReference<Map<String, ByteBuffer>> loadBytecodesFromClassPaths(
			AtomicReference<Map<String, ByteBuffer>> retrievedBytecodes,
			Map<String, ByteBuffer> extraBytecode,
			Collection<String>... classPaths
		) {
			if (retrievedBytecodes.get() == null) {
				try(ByteCodeHunter.SearchResult result = ((ClassFactoryImpl)this.classFactory).byteCodeHunter.loadInCache(
					SearchConfig.forPaths(
						classPaths
					).withScanFileCriteria(
						FileSystemItem.Criteria.forClassTypeFiles(
							((ClassFactoryImpl)this.classFactory).config.resolveStringValue(
								Configuration.Key.BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
								Configuration.DEFAULT_VALUES
							)
						)
					).optimizePaths(
						true
					)
				).find()) {
					Map<String, ByteBuffer> extraClassPathsForClassLoaderByteCodes = new HashMap<>();
					result.getItemsFoundFlatMap().values().forEach(javaClass -> {
						extraClassPathsForClassLoaderByteCodes.put(javaClass.getName(), javaClass.getByteCode());
					});
					retrievedBytecodes.set(extraClassPathsForClassLoaderByteCodes);
				}
				if (extraBytecode != null) {
					if (extraBytecode != null) {
						retrievedBytecodes.get().putAll(extraBytecode);
					}
				}
			}
			return retrievedBytecodes;
		}
		
		public Collection<Class<?>> getAllCompiledClasses() {
			Collection<Class<?>> classes = new HashSet<>();
			for(String className : uSGClassNames) {
				classes.add(get(className));
			}
			return classes;
		}
		
		public Collection<Class<?>> get(String... classesName) {
			Collection<Class<?>> classes = new HashSet<>();
			for(String className : classesName) {
				classes.add(get(className));
			}
			return classes;
		}
		
		@Override
		public void close() {
			closeResources(() -> this.classLoader == null, () -> {
				if (classLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)classLoader).unregister(this, true);
				}
				if (compilationTask != null && compilationTask.abortOrWaitForFinish().isStarted()) {
					Compilation.Result compilationResult = compilationTask.join();
					if (compilationResult != null) {
						compilationResult.close();
					}
				}
				compilationConfigSupplier = null;
				compilationConfig = null;
				compilationTask = null;
				if (useOneShotJavaCompiler) {
					((Closeable)compiler).close();
					((Closeable)classPathHelper).close();
				}
				compiler = null;
				classPathHelper = null;
				classLoader = null;
				if (byteCodesWrapper != null) {
					if (byteCodesWrapper.get() != null) {
						byteCodesWrapper.get().clear();
					}
					byteCodesWrapper.set(null);
				}
				byteCodesWrapper = null;
				this.classLoader = null;
				uSGClassNames.clear();
				uSGClassNames = null;
				classesSearchedInAdditionalClassRepositoriesForClassLoader.clear();
				classesSearchedInAdditionalClassRepositoriesForClassLoader = null;
				classesSearchedInCompilationDependenciesPaths.clear();
				classesSearchedInCompilationDependenciesPaths = null;
				additionalClassRepositoriesForClassLoader.clear();
				additionalClassRepositoriesForClassLoader = null; 
 				try {
 					((ClassFactoryImpl)this.classFactory).unregister(this);
				} catch (NullPointerException exc) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Exception while unregistering {}: classFactory is closed", this);
				}
 				this.classFactory = null;
			});
		}
	}
	/*
	public static class PojoSubTypeRetriever {
		private ClassFactory classFactory;
		private PojoSourceGenerator sourceGenerator;
		
		private PojoSubTypeRetriever(
			ClassFactory classFactory,
			PojoSourceGenerator sourceGenerator
		) {
			this.classFactory = classFactory;
			this.sourceGenerator = sourceGenerator;
		}
		
		public static PojoSubTypeRetriever create(ClassFactory classFactory, PojoSourceGenerator sourceGenerator) {
			return new PojoSubTypeRetriever(classFactory, sourceGenerator) ;
		}

		public static PojoSubTypeRetriever createDefault(ClassFactory classFactory) {
			return new PojoSubTypeRetriever(classFactory, PojoSourceGenerator.createDefault());
		}
		
		public <T> Class<T> loadOrBuildAndDefine(
				ClassLoader classLoader,
			String className,
			Class<?>... superClasses
		) {
			return loadOrBuildAndDefine(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
		}	
		
		public <T> Class<T> loadOrBuildAndDefine(
			String className,
			int options, 
			Class<?>... superClasses
		) {	
			ClassRetriever classRetriever = classFactory.loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					sourceGenerator.generate(className, options, superClasses)
				)
			);
			Class<T> cls = (Class<T>)classRetriever.get(className);
			classRetriever.close();
			return cls;
		}
		
		public <T> Class<T> loadOrBuildAndDefine(
			ClassLoader classLoader,
			String className,
			int options, 
			Class<?>... superClasses
		) {	
			ClassRetriever classRetriever = classFactory.loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					sourceGenerator.generate(className, options, superClasses)
				).useClassLoader(classLoader)
			);
			Class<T> cls = (Class<T>)classRetriever.get(className);
			classRetriever.close();
			return cls;
		}
			
	}*/
}
