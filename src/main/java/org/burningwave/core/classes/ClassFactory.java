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
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileScanConfigAbst;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

@SuppressWarnings("unchecked")
public class ClassFactory implements Component {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String DEFAULT_CLASS_LOADER = "class-factory.default-class-loader";

			public static final String CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER = "class-factory.default-class-loader.class-repositories";
			public static final String BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-factory.byte-code-hunter.search-config.check-file-options";
			public static final String CLASS_CUSTOM_REPOSITORIES = "class-factory.default-class-loader.custom-class-repositories";
		
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
	
		static {
			DEFAULT_VALUES = new LinkedHashMap<>();
			//DEFAULT_VALUES.put(Configuration.Key.DEFAULT_CLASS_LOADER + CodeExecutor.PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX, "");
			//DEFAULT_VALUES.put(Configuration.Key.DEFAULT_CLASS_LOADER + CodeExecutor.PROPERTIES_FILE_CODE_EXECUTOR_SIMPLE_NAME_KEY_SUFFIX, "DefaultClassLoaderRetrieverForClassFactory");
			//DEFAULT_VALUES.put(Key.DEFAULT_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			DEFAULT_VALUES.put(Key.DEFAULT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());

			DEFAULT_VALUES.put(
				PathHelper.Configuration.Key.PATHS_PREFIX + Key.CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER, 
				"${" + PathHelper.Configuration.Key.PATHS_PREFIX + JavaMemoryCompiler.Configuration.Key.CLASS_REPOSITORIES + "};" +
				"${" + PathHelper.Configuration.Key.PATHS_PREFIX + Configuration.Key.CLASS_CUSTOM_REPOSITORIES + "};"
			);
			DEFAULT_VALUES.put(
				Key.BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + FileScanConfigAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
		}
	}
	
	
	private PathHelper pathHelper;
	private JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	private ClassLoader defaultClassLoader;
	private ByteCodeHunter byteCodeHunter;
	private ClassPathHunter classPathHunter;
	private Supplier<ClassPathHunter> classPathHunterSupplier;
	private Supplier<ClassLoader> defaultClassLoaderSupplier;
	private Properties config;
	
	private ClassFactory(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier,
		Properties config
	) {	
		this.byteCodeHunter = byteCodeHunter;
		this.classPathHunterSupplier = classPathHunterSupplier;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
		this.defaultClassLoaderSupplier = defaultClassLoaderSupplier;
		this.config = config;
		listenTo(config);
	}
	
	public static ClassFactory create(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier,
		Properties config
	) {
		return new ClassFactory(
			byteCodeHunter,
			classPathHunterSupplier,
			javaMemoryCompiler, 
			pathHelper,
			defaultClassLoaderSupplier,
			config
		);
	}
	
	ClassLoader getDefaultClassLoader() {
		if (defaultClassLoader == null) {
			synchronized (this) {
				if (defaultClassLoader == null) {
					return defaultClassLoader = defaultClassLoaderSupplier.get();
				} else { 
					return defaultClassLoader;
				}
			}
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
		Collection<String> compilationClassPaths = 
			Optional.ofNullable(
				config.getCompilationClassPaths()
			).orElseGet(() -> 
				pathHelper.getPaths(PathHelper.Configuration.Key.MAIN_CLASS_PATHS, PathHelper.Configuration.Key.MAIN_CLASS_PATHS_EXTENSION)
			);
		
		Collection<String> classPathsForNotFoundClassesDuringCompilantion = 
			Optional.ofNullable(
				config.getClassPathsWhereToSearchNotFoundClassesDuringCompilation()
			).orElseGet(() -> 
				pathHelper.getPaths(JavaMemoryCompiler.Configuration.Key.CLASS_REPOSITORIES)
			);
		
		Collection<String> classPathsForNotFoundClassesDuringLoading = 
			Optional.ofNullable(
				config.getClassPathsWhereToSearchNotFoundClassesDuringLoading()
			).orElseGet(() -> 
				pathHelper.getPaths(Configuration.Key.CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER)
			);
		
		ClassLoader classLoader = Optional.ofNullable(
			config.getClassLoader()
		).orElseGet(() -> 
			getDefaultClassLoader()
		);
		
		return loadOrBuildAndDefine(
			config.isUseOneShotJavaCompiler(),
			compilationClassPaths,
			classPathsForNotFoundClassesDuringCompilantion, 
			classPathsForNotFoundClassesDuringLoading,
			config.isStoreCompiledClasses(),
			classLoader,
			config.getUnitSourceGenerators()
		);
	}
	
	private ClassRetriever loadOrBuildAndDefine(
		boolean useOneShotJavaCompiler,
		Collection<String> compilationClassPaths,
		Collection<String> classPathsForNotFoundClassesDuringCompilantion,
		Collection<String> classPathsForNotFoundClassesDuringLoading,
		boolean storeCompiledClasses,
		ClassLoader classLoader,
		Collection<UnitSourceGenerator> unitsCode
	) {
		try {
			Set<String> classesName = new HashSet<>();
			unitsCode.forEach(unitCode -> 
				unitCode.getAllClasses().entrySet().forEach(entry -> {
					entry.getValue().addConcretizedType(TypeDeclarationSourceGenerator.create(Virtual.class));
					classesName.add(entry.getKey());
				})
			);
			Map<String, Class<?>> classes = new HashMap<>();
			AtomicReference<Map<String, ByteBuffer>> retrievedBytecodes = new AtomicReference<>();
			for (String className : classesName) {
				try {
					classes.put(className, classLoader.loadClass(className));
				} catch (Throwable exc) {
					Map<String, ByteBuffer> compiledByteCodes = build(
						useOneShotJavaCompiler,
						compilationClassPaths,
						classPathsForNotFoundClassesDuringCompilantion, 
						unitsCode, storeCompiledClasses
					);
					return new ClassRetriever() {
						@Override
						public Class<?> get(String className, Map<String, ByteBuffer> additionalByteCodes) {
							try {
								Map<String, ByteBuffer> finalByteCodes = compiledByteCodes;
								if (additionalByteCodes != null) {
									finalByteCodes = new HashMap<>(compiledByteCodes);
									finalByteCodes.putAll(additionalByteCodes);
								}
								return ClassLoaders.loadOrDefineByByteCode(className, finalByteCodes, classLoader);
							} catch (Throwable innExc) {
								return ThrowingSupplier.get(() -> {
									return ClassLoaders.loadOrDefineByByteCode(className, 
										loadBytecodesFromClassPaths(
											retrievedBytecodes,
											classPathsForNotFoundClassesDuringLoading,
											compiledByteCodes,
											additionalByteCodes
										).get(), classLoader
									);
								});
							}
						}
					};
					
				}
			}
			logInfo("Classes {} loaded by classloader {} without building", String.join(", ", classes.keySet()), classLoader);
			return new ClassRetriever() {
				@Override
				public Class<?> get(String className, Map<String, ByteBuffer> additionalByteCodes) {
					try {
						return classLoader.loadClass(className);
					} catch (Throwable exc) {
						try {
							return ClassLoaders.loadOrDefineByByteCode(className, Optional.ofNullable(additionalByteCodes).orElseGet(HashMap::new), classLoader);
						} catch (Throwable exc2) {
							return ThrowingSupplier.get(() -> 
								ClassLoaders.loadOrDefineByByteCode(
									className,
									loadBytecodesFromClassPaths(
										retrievedBytecodes, 
										classPathsForNotFoundClassesDuringLoading,
										additionalByteCodes
									).get(), 
									classLoader
								)
							);
						}
					}
				}
			};
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	@SafeVarargs
	private final AtomicReference<Map<String, ByteBuffer>> loadBytecodesFromClassPaths(
		AtomicReference<Map<String, ByteBuffer>> retrievedBytecodes,
		Collection<String> classPaths,
		Map<String, ByteBuffer>... extraBytecodes
	) {
		if (retrievedBytecodes.get() == null) {
			synchronized (retrievedBytecodes) {
				if (retrievedBytecodes.get() == null) {
					try(ByteCodeHunter.SearchResult result = byteCodeHunter.loadInCache(
						SearchConfig.forPaths(
							classPaths
						).deleteFoundItemsOnClose(
							false
						).checkFileOptions(
							FileScanConfigAbst.Configuration.parseCheckFileOptionsValue(
								IterableObjectHelper.get(
									config,
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
				}
			}
		}
		if (extraBytecodes != null && extraBytecodes.length > 0) {
			for (Map<String, ByteBuffer> extraBytecode : extraBytecodes) {
				if (extraBytecode != null) {
					synchronized(retrievedBytecodes) {
						retrievedBytecodes.get().putAll(extraBytecode);
					}
				}
			}
		}
		return retrievedBytecodes;
	}
	
	private Map<String, ByteBuffer> build(
		boolean useOneShotCompiler,
		Collection<String> mainClassPaths,
		Collection<String> extraClassPaths,
		Collection<UnitSourceGenerator> unitsCode,
		boolean storeCompiledClasses
	) {
		return build0(
			useOneShotCompiler,
			mainClassPaths,
			extraClassPaths,
			unitsCode.stream().map(unitCode -> unitCode.make()).collect(Collectors.toList()),
			storeCompiledClasses
		);
	}
	
	private Map<String, ByteBuffer> build0(
		boolean useOneShotCompiler,
		Collection<String> compilationClassPaths,
		Collection<String> classPathsForNotFoundClassesDuringCompilantion,
		Collection<String> unitsCode,
		boolean storeCompiledClasses
	) {
		logInfo("Try to compile: \n\n{}\n",String.join("\n", unitsCode));
		if (useOneShotCompiler) {
			try (JavaMemoryCompiler compiler = JavaMemoryCompiler.create(
				pathHelper,
				getClassPathHunter(),
				config
			)) {
				return compiler.compile(
					unitsCode,
					compilationClassPaths, 
					classPathsForNotFoundClassesDuringCompilantion,
					storeCompiledClasses
				);
			}
		} else {
			return this.javaMemoryCompiler.compile(
				unitsCode,
				compilationClassPaths, 
				classPathsForNotFoundClassesDuringCompilantion,
				storeCompiledClasses
			);
		}
	}
	
	public PojoSubTypeRetriever createPojoSubTypeRetriever(PojoSourceGenerator sourceGenerator) {
		return PojoSubTypeRetriever.create(this, sourceGenerator);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(String className, Class<?>... superClasses) {
		return loadOrBuildAndDefinePojoSubType(getDefaultClassLoader(), className, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(String className, int options, Class<?>... superClasses) {
		return loadOrBuildAndDefinePojoSubType(getDefaultClassLoader(), className, options, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(ClassLoader classLoader, String className, int options, Class<?>... superClasses) {
		return pojoSubTypeRetriever.loadOrBuildAndDefine(classLoader, className, options, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(ClassLoader classLoader, String className, Class<?>... superClasses) {
		return pojoSubTypeRetriever.loadOrBuildAndDefine(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
	}
	
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(int parametersCount) {
		return loadOrBuildAndDefineFunctionSubType(getDefaultClassLoader(), parametersCount);
	}
	
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "FunctionFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generateFunction(className, paramsL)
		);
	}
	
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(int parametersCount) {
		return loadOrBuildAndDefineConsumerSubType(getDefaultClassLoader(), parametersCount);
	}
	
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "ConsumerFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generateConsumer(className, paramsL)
		);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(int parametersLength) {
		return loadOrBuildAndDefinePredicateSubType(getDefaultClassLoader(), parametersLength);
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
		return (Class<T>) loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
				unitSourceGeneratorSupplier.apply(className, parametersLength)
			).useClassLoader(
				classLoader
			)
		).get(
			className
		);
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
		
		public Class<?> loadOrBuildAndDefine(
			String className,
			int options, 
			Class<?>... superClasses
		) {
			return classFactory.loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					sourceGenerator.create(className, options, superClasses)
				)
			).get(
				className
			);
		}
		
		public <T> Class<T> loadOrBuildAndDefine(
			ClassLoader classLoader,
			String className,
			int options, 
			Class<?>... superClasses
		) {
			return (Class<T>) classFactory.loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					sourceGenerator.create(className, options, superClasses)
				).useClassLoader(classLoader)
			).get(
				className
			);
		}
			
	}
	

	public static abstract class ClassRetriever {
		
		public abstract Class<?> get(String className, Map<String, ByteBuffer> additionalByteCodes);
		
		public Class<?> get(String className) {
			return get(className, null);
		}
		
		public Collection<Class<?>> get(String... classesName) {
			Collection<Class<?>> classes = new HashSet<>();
			for(String className : classesName) {
				classes.add(get(className, null));
			}
			return classes;
		}
		
	}
}
