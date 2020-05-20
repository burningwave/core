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
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.Executor;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileScanConfigAbst;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;


public class ClassFactory implements Component {
	public static final String DEFAULT_CLASS_LOADER_CONFIG_KEY = "class-factory.default-class-loader";
	public static final String CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY = "class-factory.java-memory-compiler.class-repositories";
	public static final String CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER_CONFIG_KEY = "class-factory.default-class-loader.class-repositories";
	public static final String BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS_CONFIG_KEY = "class-factory.byte-code-hunter.search-config.check-file-options";
	public final static String PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX = ".imports";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	private ClassLoader defaultClassLoader;
	private ByteCodeHunter byteCodeHunter;
	private ClassPathHunter classPathHunter;
	private Supplier<ClassPathHunter> classPathHunterSupplier;
	private Supplier<ClassLoader> defaultClassLoaderSupplier;
	private IterableObjectHelper iterableObjectHelper;	
	private Supplier<IterableObjectHelper> iterableObjectHelperSupplier;
	private Properties config;
	
	private ClassFactory(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		SourceCodeHandler sourceCodeHandler,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier,
		Supplier<IterableObjectHelper> iterableObjectHelperSupplier,
		Properties config
	) {	
		this.byteCodeHunter = byteCodeHunter;
		this.classPathHunterSupplier = classPathHunterSupplier;
		this.sourceCodeHandler = sourceCodeHandler;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
		this.defaultClassLoaderSupplier = defaultClassLoaderSupplier;
		this.iterableObjectHelperSupplier = iterableObjectHelperSupplier;
		this.config = config;
		listenTo(config);
	}
	
	public static ClassFactory create(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		SourceCodeHandler sourceCodeHandler,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier,
		Supplier<IterableObjectHelper> iterableObjectHelperSupplier,
		Properties config
	) {
		return new ClassFactory(
			byteCodeHunter,
			classPathHunterSupplier,
			sourceCodeHandler, 
			javaMemoryCompiler, 
			pathHelper,
			defaultClassLoaderSupplier,
			iterableObjectHelperSupplier,
			config
		);
	}
	
	protected IterableObjectHelper getIterableObjectHelper() {
		return iterableObjectHelper != null ?
			iterableObjectHelper :
			(iterableObjectHelper = iterableObjectHelperSupplier.get());
	}
	
	private ClassLoader getDefaultClassLoader() {
		return defaultClassLoader != null? defaultClassLoader :
			(defaultClassLoader = defaultClassLoaderSupplier.get());
	}
	
	private ClassPathHunter getClassPathHunter() {
		return classPathHunter != null? classPathHunter :
			(classPathHunter = classPathHunterSupplier.get());
	}
	
    /**
     * @deprecated
     * <p> Use {@link ClassFactory#loadOrBuildAndDefine(UnitSourceGenerator...)} instead.
     */
    @Deprecated
	public ClassRetriever buildAndLoadOrUpload(UnitSourceGenerator... unitsCode) {
		return loadOrBuildAndDefine(LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitsCode));
	}
	
	
	public ClassRetriever loadOrBuildAndDefine(UnitSourceGenerator... unitsCode) {
		return loadOrBuildAndDefine(LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitsCode));
	}
	
	public ClassRetriever loadOrBuildAndDefine(LoadOrBuildAndDefineConfig config) {
		Collection<String> compilationClassPaths = 
			Optional.ofNullable(
				config.getCompilationClassPaths()
			).orElseGet(() -> 
				pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION)
			);
		
		Collection<String> classPathsForNotFoundClassesDuringCompilantion = 
			Optional.ofNullable(
				config.getClassPathsWhereToSearchNotFoundClassesDuringCompilation()
			).orElseGet(() -> 
				pathHelper.getPaths(CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY)
			);
		
		Collection<String> classPathsForNotFoundClassesDuringLoading = 
			Optional.ofNullable(
				config.getClassPathsWhereToSearchNotFoundClassesDuringLoading()
			).orElseGet(() -> 
				pathHelper.getPaths(CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER_CONFIG_KEY)
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
			classLoader,
			config.getUnitSourceGenerators()
		);
	}
	
	private ClassRetriever loadOrBuildAndDefine(
		boolean useOneShotJavaCompiler,
		Collection<String> compilationClassPaths,
		Collection<String> classPathsForNotFoundClassesDuringCompilantion,
		Collection<String> classPathsForNotFoundClassesDuringLoading,
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
						unitsCode
					);
					return (clsName, additionalByteCodes) -> {
						try {
							Map<String, ByteBuffer> finalByteCodes = compiledByteCodes;
							if (additionalByteCodes != null) {
								finalByteCodes = new HashMap<>(compiledByteCodes);
								finalByteCodes.putAll(additionalByteCodes);
							}
							return ClassLoaders.loadOrDefineByByteCode(clsName, finalByteCodes, classLoader);
						} catch (Throwable innExc) {
							return ThrowingSupplier.get(() -> {
								return ClassLoaders.loadOrDefineByByteCode(clsName, 
									loadBytecodesFromClassPaths(
										retrievedBytecodes,
										classPathsForNotFoundClassesDuringLoading,
										compiledByteCodes,
										additionalByteCodes
									).get(), classLoader
								);
							});
						}
					};
					
				}
			}
			logInfo("Classes {} loaded by classloader {} without building", String.join(", ", classes.keySet()), classLoader);
			return (clsName, additionalByteCodes) -> {
				try {
					return classLoader.loadClass(clsName);
				} catch (Throwable exc) {
					try {
						return ClassLoaders.loadOrDefineByByteCode(clsName, Optional.ofNullable(additionalByteCodes).orElseGet(HashMap::new), classLoader);
					} catch (Throwable exc2) {
						return ThrowingSupplier.get(() -> 
							ClassLoaders.loadOrDefineByByteCode(
								clsName,
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
							FileScanConfigAbst.parseCheckFileOptionsValue(
								(String)config.get(ClassFactory.BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS_CONFIG_KEY),
								FileScanConfigAbst.CHECK_FILE_OPTIONS_DEFAULT_VALUE
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
		Collection<UnitSourceGenerator> unitsCode
	) {
		return build0(
			useOneShotCompiler,
			mainClassPaths,
			extraClassPaths,
			unitsCode.stream().map(unitCode -> unitCode.make()).collect(Collectors.toList())
		);
	}
	
	private Map<String, ByteBuffer> build0(
		boolean useOneShotCompiler,
		Collection<String> compilationClassPaths,
		Collection<String> classPathsForNotFoundClassesDuringCompilantion,
		Collection<String> unitsCode
	) {
		logInfo("Try to compile: \n\n{}\n",String.join("\n", unitsCode));
		return (useOneShotCompiler ?
			JavaMemoryCompiler.create(
				pathHelper,
				sourceCodeHandler,
				getClassPathHunter(),
				config
			) :
			this.javaMemoryCompiler
		).compile(
			unitsCode,
			compilationClassPaths, 
			classPathsForNotFoundClassesDuringCompilantion
		);
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
			(className, paramsL) -> sourceCodeHandler.generateFunction(className, paramsL)
		);
	}
	
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(int parametersCount) {
		return loadOrBuildAndDefineConsumerSubType(getDefaultClassLoader(), parametersCount);
	}
	
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "ConsumerFor", "Parameters", parametersLength,
			(className, paramsL) -> sourceCodeHandler.generateConsumer(className, paramsL)
		);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(int parametersLength) {
		return loadOrBuildAndDefinePredicateSubType(getDefaultClassLoader(), parametersLength);
	}
	
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "PredicateFor", "Parameters", parametersLength,
			(className, paramsL) -> sourceCodeHandler.generatePredicate(className, paramsL)
		);
	}
	
	@SuppressWarnings("unchecked")
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
	
	public <T extends Executor> Class<T> loadOrBuildAndDefineExecutorSubType(String className, StatementSourceGenerator statement) {
		return loadOrBuildAndDefineExecutorSubType(getDefaultClassLoader(), className, statement);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Executor> Class<T> loadOrBuildAndDefineExecutorSubType(ClassLoader classLoader, String className, StatementSourceGenerator statement) {
		return (Class<T>) loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
				sourceCodeHandler.generateExecutor(className, statement)
			).useClassLoader(
				classLoader
			)
		).get(
			className
		);
	}
	
	public <T> T execute(ExecuteConfig.ForProperties config) {
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = getDefaultClassLoader();
		}
		
		java.util.Properties properties = config.getProperties();
		if (properties == null) {
			if (config.getFilePath() == null) {
				properties = this.config; 
			} else {
				Properties tempProperties = new Properties();
				if (config.isAbsoluteFilePath()) {
					ThrowingRunnable.run(() -> 
						tempProperties.load(FileSystemItem.ofPath(config.getFilePath()).toInputStream())
					);
				} else {
					ThrowingRunnable.run(() ->
						tempProperties.load(pathHelper.getResourceAsStream(config.getFilePath()))
					);
				}
				properties = tempProperties;
			}
			
		}
		return execute(parentClassLoader, properties, config.getPropertyName(), config.getDefaultValues(), config.getParams());
	}		
	
	private <T> T execute(
		ClassLoader classLoaderParent,
		java.util.Properties properties, 
		String key,
		Map<String, String> defaultValues,
		Object... params
	) {	
		StatementSourceGenerator statement = StatementSourceGenerator.createSimple().setElementPrefix("\t");
		if (params != null && params.length > 0) {
			for (Object param : params) {
				statement.useType(param.getClass());
			}
		}
		String importFromConfig = getIterableObjectHelper().get(properties, key + PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX, defaultValues);
		if (Strings.isNotEmpty(importFromConfig)) {
			Arrays.stream(importFromConfig.split(";")).forEach(imp -> {
				statement.useType(imp);
			});
		}
		String code = getIterableObjectHelper().get(properties, key, defaultValues);
		if (code.contains(";")) {
			for (String codeRow : code.split(";")) {
				statement.addCodeRow(codeRow + ";");
			}
		} else {
			statement.addCodeRow(code.contains("return")?
				code:
				"return (T)" + code + ";"
			);
		}
		return execute(
			classLoaderParent, statement, params
		);
	}
	
	public <T> T execute(
		ExecuteConfig.ForStatementSourceGenerator config
	) {	
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = getDefaultClassLoader();
		}
		return execute(parentClassLoader, config.getStatement(), config.getParams());
	}
	
	private <T> T execute(
		ClassLoader classLoaderParentOfOneShotClassLoader,
		StatementSourceGenerator statement,
		Object... parameters
	) {	
		return ThrowingSupplier.get(() -> {
			try (MemoryClassLoader memoryClassLoader = 
				MemoryClassLoader.create(
					classLoaderParentOfOneShotClassLoader
				)
			) {
				String packageName = Executor.class.getPackage().getName();
				Class<? extends Executor> executableClass = loadOrBuildAndDefineExecutorSubType(
					memoryClassLoader, packageName + ".CodeExecutor_" + UUID.randomUUID().toString().replaceAll("-", ""), statement
				);
				Executor executor = Constructors.newInstanceOf(executableClass);
				ComponentSupplier componentSupplier = null;
				if (parameters != null && parameters.length > 0) {
					for (Object param : parameters) {
						if (param instanceof ComponentSupplier) {
							componentSupplier = (ComponentSupplier) param;
							break;
						}
					}
				}
				T retrievedElement = executor.execute(componentSupplier, parameters);
				return retrievedElement;
			}
		});
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
		
		@SuppressWarnings("unchecked")
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
	
	@FunctionalInterface
	public static interface ClassRetriever {
		
		public Class<?> get(String className, Map<String, ByteBuffer> additionalByteCodes);
		
		public default Class<?> get(String className) {
			return get(className, null);
		}
		
		public default Collection<Class<?>> get(String... classesName) {
			Collection<Class<?>> classes = new HashSet<>();
			for(String className : classesName) {
				classes.add(get(className, null));
			}
			return classes;
		}
		
	}
}
