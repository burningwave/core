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
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.JavaMemoryCompiler.Compilation;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

@SuppressWarnings("unchecked")
public class ClassFactory implements Component {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String DEFAULT_CLASS_LOADER = "class-factory.default-class-loader";

			public static final String CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER = PathHelper.Configuration.Key.PATHS_PREFIX + "class-factory.default-class-loader.class-repositories";
			public static final String ADDITIONAL_CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER = PathHelper.Configuration.Key.PATHS_PREFIX + "class-factory.default-class-loader.additional-class-repositories";
			public static final String BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-factory.byte-code-hunter.search-config.check-file-option";
					
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
	
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			//DEFAULT_VALUES.put(Key.DEFAULT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
			defaultValues.put(Configuration.Key.DEFAULT_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Key.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.DEFAULT_CLASS_LOADER + ".additional-imports}" + CodeExecutor.Configuration.Key.CODE_LINE_SEPARATOR + 
				PathScannerClassLoader.class.getName() + CodeExecutor.Configuration.Key.CODE_LINE_SEPARATOR
			);
			defaultValues.put(Configuration.Key.DEFAULT_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_CODE_EXECUTOR_NAME_SUFFIX, ClassFactory.class.getPackage().getName() + ".DefaultClassLoaderRetrieverForClassFactory");
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
				"${" + ClassPathScannerAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	
	private PathHelper pathHelper;
	private ClassPathHelper classPathHelper;
	private JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	private ClassLoader defaultClassLoader;
	private ByteCodeHunter byteCodeHunter;
	private ClassPathHunter classPathHunter;
	private Supplier<ClassPathHunter> classPathHunterSupplier;
	private Object defaultClassLoaderOrDefaultClassLoaderSupplier;
	private Supplier<ClassLoader> defaultClassLoaderSupplier;
	private Collection<ClassRetriever> classRetrievers;
	private Consumer<ClassLoader> classLoaderResetter;
	private Properties config;
	
	private ClassFactory(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Object defaultClassLoaderOrDefaultClassLoaderSupplier,
		Consumer<ClassLoader> classLoaderResetter,
		Properties config
	) {	
		this.byteCodeHunter = byteCodeHunter;
		this.classPathHunterSupplier = classPathHunterSupplier;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.classPathHelper = classPathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
		this.defaultClassLoaderOrDefaultClassLoaderSupplier = defaultClassLoaderOrDefaultClassLoaderSupplier;
		this.classLoaderResetter = classLoaderResetter;
		this.classRetrievers = new CopyOnWriteArrayList<>();
		this.config = config;
		listenTo(config);
	}
	
	public static ClassFactory create(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Object defaultClassLoaderSupplier,
		Consumer<ClassLoader> classLoaderResetter,
		Properties config
	) {
		return new ClassFactory(
			byteCodeHunter,
			classPathHunterSupplier,
			javaMemoryCompiler, 
			pathHelper,
			classPathHelper,
			defaultClassLoaderSupplier,
			classLoaderResetter,
			config
		);
	}
	
	private String getOperationId(String operation) {
		return Objects.getId(this) + "_" + operation;
	}
	
	ClassLoader getDefaultClassLoader(Object client) {
		if (defaultClassLoaderSupplier != null) {
			ClassLoader classLoader = defaultClassLoaderSupplier.get();
			if (defaultClassLoader != classLoader) {
				synchronized(classLoader) {
					if (defaultClassLoader != classLoader) {
						ClassLoader oldClassLoader = this.defaultClassLoader;
						if (oldClassLoader != null && oldClassLoader instanceof MemoryClassLoader) {
							((MemoryClassLoader)oldClassLoader).unregister(this, true);
						}
						if (classLoader instanceof MemoryClassLoader) {
							if (!((MemoryClassLoader)classLoader).register(this)) {
								classLoader = getDefaultClassLoader(client);
							} else {
								((MemoryClassLoader)classLoader).register(client);
							}
						}
						this.defaultClassLoader = classLoader;
					}
				}
			}
			return classLoader;
		}
		if (defaultClassLoader == null) {
			return Synchronizer.execute(getOperationId("getDefaultClassLoader"), () -> {
				if (defaultClassLoader == null) {
					Object classLoaderOrClassLoaderSupplier = ((Supplier<?>)this.defaultClassLoaderOrDefaultClassLoaderSupplier).get();
					if (classLoaderOrClassLoaderSupplier instanceof ClassLoader) {
						this.defaultClassLoader = (ClassLoader)classLoaderOrClassLoaderSupplier;
						if (defaultClassLoader instanceof MemoryClassLoader) {
							((MemoryClassLoader)defaultClassLoader).register(this);
							((MemoryClassLoader)defaultClassLoader).register(client);
						}
					} else if (classLoaderOrClassLoaderSupplier instanceof Supplier) {
						this.defaultClassLoaderSupplier = (Supplier<ClassLoader>) classLoaderOrClassLoaderSupplier;
						return getDefaultClassLoader(client);
					}
				}
				return defaultClassLoader;
			});
			
		}
		return defaultClassLoader;
	}
	
	private ClassPathHunter getClassPathHunter() {
		return classPathHunter != null? classPathHunter :
			(classPathHunter = classPathHunterSupplier.get());
	}
	
	public ClassRetriever loadOrBuildAndDefine(UnitSourceGenerator... unitsCode) {
		return loadOrBuildAndDefine(LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitsCode));
	}
	
	public <L extends LoadOrBuildAndDefineConfigAbst<L>> ClassRetriever loadOrBuildAndDefine(L config) {
		if (config.isVirtualizeClassesEnabled()) {
			config.addClassPaths(pathHelper.getBurningwaveRuntimeClassPath());
		}
		return loadOrBuildAndDefine(
			config.getClassesName(),
			config.getCompileConfigSupplier(),			
			config.isUseOneShotJavaCompilerEnabled(),
			IterableObjectHelper.merge(
				() -> config.getClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(),
				() -> config.getAdditionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(),
				() -> {
					Collection<String> classRepositoriesForNotFoundClasses = pathHelper.getPaths(
						Configuration.Key.CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER
					);
					if (!classRepositoriesForNotFoundClasses.isEmpty()) {
						config.addClassRepositoriesWhereToSearchNotFoundClasses(classRepositoriesForNotFoundClasses);
					}
					return classRepositoriesForNotFoundClasses;
				}
			),
			(client) -> Optional.ofNullable(
				config.getClassLoader()
			).orElseGet(() -> 
				getDefaultClassLoader(client)
			)
		);
	}
	
	private ClassRetriever loadOrBuildAndDefine(
		Collection<String> classNames,
		Supplier<CompilationConfig> compileConfigSupplier,		
		boolean useOneShotJavaCompiler,
		Collection<String> additionalClassRepositoriesForClassLoader,
		Function<Object, ClassLoader> classLoaderSupplier
	) {
		try {
			Object temporaryClient = new Object(){};
			ClassLoader classLoader = classLoaderSupplier.apply(temporaryClient);
			Function<ClassRetriever, ClassLoader> classLoaderSupplierForClassRetriever = (classRetriever) -> {
				if (classLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)classLoader).register(classRetriever);
					((MemoryClassLoader)classLoader).unregister(temporaryClient, true);
					if (classLoader != defaultClassLoader) {
						((MemoryClassLoader) classLoader).unregister(this, true);
					}
				}
				return classLoader;
			};
			
			return new ClassRetriever(
				this,
				classLoaderSupplierForClassRetriever,
				compileConfigSupplier,
				useOneShotJavaCompiler,
				additionalClassRepositoriesForClassLoader,
				classNames
			);					


		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}

	
	public PojoSubTypeRetriever createPojoSubTypeRetriever(PojoSourceGenerator sourceGenerator) {
		return PojoSubTypeRetriever.create(this, sourceGenerator);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(String className, Class<?>... superClasses) {
		return loadOrBuildAndDefinePojoSubType(null, className, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(String className, int options, Class<?>... superClasses) {
		return loadOrBuildAndDefinePojoSubType(null, className, options, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(ClassLoader classLoader, String className, int options, Class<?>... superClasses) {
		return pojoSubTypeRetriever.loadOrBuildAndDefine(classLoader, className, options, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(ClassLoader classLoader, String className, Class<?>... superClasses) {
		return pojoSubTypeRetriever.loadOrBuildAndDefine(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(int parametersCount) {
		return loadOrBuildAndDefineFunctionSubType(null, parametersCount);
	}
	
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "FunctionFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generateFunction(className, paramsL)
		);
	}
	
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(int parametersCount) {
		return loadOrBuildAndDefineConsumerSubType(null, parametersCount);
	}
	
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "ConsumerFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generateConsumer(className, paramsL)
		);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(int parametersLength) {
		return loadOrBuildAndDefinePredicateSubType(null, parametersLength);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "PredicateFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generatePredicate(className, paramsL)
		);
	}
	
	private <T> Class<T> loadOrBuildAndDefineFunctionInterfaceSubType(
		ClassLoader classLoader,
		String classNamePrefix, 
		String classNameSuffix,
		int parametersLength,
		BiFunction<String, Integer, UnitSourceGenerator> unitSourceGeneratorSupplier
	) {
		String functionalInterfaceName = classNamePrefix + parametersLength +	classNameSuffix;
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		ClassRetriever classRetriever = loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
				unitSourceGeneratorSupplier.apply(className, parametersLength)
			).useClassLoader(
				classLoader
			)
		);
		Class<T> cls = (Class<T>)classRetriever.get(className);
		classRetriever.close();
		return cls;
	}
	
	
	
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
		
		public <T> Class<T> getOrBuild(
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
					sourceGenerator.create(className, options, superClasses)
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
					sourceGenerator.create(className, options, superClasses)
				).useClassLoader(classLoader)
			);
			Class<T> cls = (Class<T>)classRetriever.get(className);
			classRetriever.close();
			return cls;
		}
			
	}
	
	boolean register(ClassRetriever classRetriever) {
		classRetrievers.add(classRetriever);
		return true;
	}
	
	boolean unregister(ClassRetriever classRetriever) {
		classRetrievers.remove(classRetriever);
		return true;
	}
	
	public void closeClassRetrievers() {
		Synchronizer.execute(getOperationId("closeClassRetrievers"), () -> {
			Collection<ClassRetriever> classRetrievers = this.classRetrievers;
			if (classRetrievers != null) {
				Iterator<ClassRetriever> classRetrieverIterator = classRetrievers.iterator();		
				while(classRetrieverIterator.hasNext()) {
					ClassRetriever classRetriever = classRetrieverIterator.next();
					classRetriever.close();
				}
			}
		});
	}
	
	public void reset(boolean closeClassRetrievers) {
		if (closeClassRetrievers) {
			closeClassRetrievers();
		}
		ClassLoader defaultClassLoader = this.defaultClassLoader;
		if (defaultClassLoader != null) {
			this.defaultClassLoader = null;
			classLoaderResetter.accept(defaultClassLoader);
			if (defaultClassLoader instanceof MemoryClassLoader) {
				((MemoryClassLoader)defaultClassLoader).unregister(this, true);
			}
		}		
	}
	
	@Override
	public void close() {
		closeResources(() -> this.classRetrievers == null, () -> {
			unregister(config);
			closeClassRetrievers();
			BackgroundExecutor.createTask(() -> {
					this.classRetrievers.clear();
					this.classRetrievers = null;
				}
			).submit();
			pathHelper = null;
			javaMemoryCompiler = null;
			pojoSubTypeRetriever = null;	
			if (defaultClassLoader instanceof MemoryClassLoader) {
				((MemoryClassLoader)defaultClassLoader).unregister(this, true);
			}
			defaultClassLoader = null;
			byteCodeHunter = null;
			classPathHunter = null;
			classPathHunterSupplier = null;
			defaultClassLoaderOrDefaultClassLoaderSupplier = null;
			defaultClassLoaderOrDefaultClassLoaderSupplier = null;
			defaultClassLoaderSupplier = null;
			classLoaderResetter = null;		
			config = null;
		});
	}

	public static class ClassRetriever implements Component {
		ClassLoader classLoader;
		ClassFactory classFactory;
		Supplier<CompilationConfig> compilationConfigSupplier;
		CompilationConfig compilationConfig;
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
		
		private ClassRetriever(
			ClassFactory classFactory,
			Function<ClassRetriever, ClassLoader> classLoaderSupplier,
			Supplier<CompilationConfig> compileConfigSupplier,
			boolean useOneShotJavaCompiler,
			Collection<String> additionalClassRepositoriesForClassLoader,
			Collection<String> uSGClassNames
		) {
			this.classLoader = classLoaderSupplier.apply(this);
			this.classFactory = classFactory;
			this.classFactory.register(this);
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
							if (!classPathHelper.computeClassPathsAndAddThemToClassLoader(
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
						if (!classPathHelper.computeClassPathsAndAddThemToClassLoader(
							classLoader, classPaths, classPathsToBeRefreshed, className, notFoundClasses
						).isEmpty()) {
							return get(className);
						}
						throw exc;
					}
				} catch (ClassNotFoundException | NoClassDefFoundError exc) {
					return ClassLoaders.loadOrDefineByByteCode(className, 
						loadBytecodesFromClassPaths(
							this.byteCodesWrapper,
							getCompilationResult().getCompiledFiles(),
							additionalClassRepositoriesForClassLoader
						).get(), classLoader
					);
				}
			} catch (ClassNotFoundException | NoClassDefFoundError exc) {
				return ThrowingSupplier.get(() -> {
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
						classPathHelper = !useOneShotJavaCompiler ? classFactory.classPathHelper : ClassPathHelper.create(
							this.classFactory.getClassPathHunter(),
							this.classFactory.config
						);
					
						compiler = !useOneShotJavaCompiler ?
							classFactory.javaMemoryCompiler :
							JavaMemoryCompiler.create(
								classFactory.pathHelper,
								classPathHelper,
								this.classFactory.config
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
				throw Throwables.toRuntimeException(getCompilationTask().getException());
			}
			return compilationResult;
		}

		private CompilationConfig getCompilationConfig() {
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
				try(ByteCodeHunter.SearchResult result = classFactory.byteCodeHunter.loadInCache(
					SearchConfig.forPaths(
						classPaths
					).withScanFileCriteria(
						FileSystemItem.Criteria.forClassTypeFiles(
							classFactory.config.resolveStringValue(
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
				if (compilationTask != null) {
					compilationTask.join().close();
				}
				compilationConfigSupplier = null;
				compilationConfig = null;
				compilationTask = null;
				if (useOneShotJavaCompiler) {
					compiler.close();
					classPathHelper.close();
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
					this.classFactory.unregister(this);
				} catch (NullPointerException exc) {
					logWarn("Exception while unregistering {}: classFactory is closed", this);
				}
 				this.classFactory = null;
			});
		}
	}
}
