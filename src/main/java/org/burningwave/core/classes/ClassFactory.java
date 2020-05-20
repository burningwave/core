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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.Executor;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;
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
	
	public Map<String, ByteBuffer> build(boolean useOneShotCompiler, Collection<String> mainClassPaths, Collection<String> extraClassPaths, UnitSourceGenerator... unitsCode) {
		return build(useOneShotCompiler, mainClassPaths, extraClassPaths, Arrays.asList(unitsCode).stream().map(unitCode -> unitCode.make()).collect(Collectors.toList()));
	}
	
	public Map<String, ByteBuffer> build(boolean useOneShotCompiler, Collection<String> mainClassPaths, Collection<String> extraClassPaths, String... unitsCode) {
		return build(useOneShotCompiler, mainClassPaths, extraClassPaths, unitsCode);
	}
	
	public Map<String, ByteBuffer> build(boolean useOneShotCompiler, Collection<String> mainClassPaths, Collection<String> extraClassPaths, Collection<String> unitsCode) {
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
			mainClassPaths, 
			extraClassPaths
		);
	}
	
	public ClassRetriever buildAndLoadOrUpload(
		Collection<String> compilationClassPaths,
		Collection<String> compilationClassPathsForNotFoundClasses,
		Collection<String> classLoaderClassPaths,
		UnitSourceGenerator... unitsCode
	) {
		return buildAndLoadOrUploadTo(
			false,
			compilationClassPaths,
			compilationClassPathsForNotFoundClasses,
			classLoaderClassPaths,
			getDefaultClassLoader(),
			unitsCode
		);
	}
	
	public ClassRetriever buildAndLoadOrUpload(UnitSourceGenerator... unitsCode) {
		return buildAndLoadOrUploadTo(getDefaultClassLoader(), unitsCode);
	}
	
	public ClassRetriever buildAndLoadOrUploadTo(ClassLoader classLoader, UnitSourceGenerator... unitsCode) {
		return buildAndLoadOrUploadTo(
			false,
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY), 
			pathHelper.getPaths(CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER_CONFIG_KEY), classLoader, unitsCode
		);
	}
	
	public ClassRetriever buildAndLoadOrUploadTo(
		boolean useOneShotJavaCompiler,
		Collection<String> compilationClassPaths,
		Collection<String> compilationClassPathsForNotFoundClasses,
		Collection<String> classLoaderClassPaths,
		ClassLoader classLoader,
		UnitSourceGenerator... unitsCode
	) {
		try {
			Set<String> classesName = new HashSet<>();
			Arrays.stream(unitsCode).forEach(unitCode -> 
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
					Map<String, ByteBuffer> compiledByteCodes = build(useOneShotJavaCompiler, compilationClassPaths, compilationClassPathsForNotFoundClasses, unitsCode);
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
										classLoaderClassPaths,
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
									classLoaderClassPaths,
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
	
	public PojoSubTypeRetriever createPojoSubTypeRetriever(PojoSourceGenerator sourceGenerator) {
		return PojoSubTypeRetriever.create(this, sourceGenerator);
	}
	
	public <T> Class<T> buildPojoSubTypeAndLoadOrUpload(String className, Class<?>... superClasses) {
		return buildPojoSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), className, superClasses);
	}
	
	public <T> Class<T> buildPojoSubTypeAndLoadOrUploadTo(ClassLoader classLoader, String className, Class<?>... superClasses) {
		return pojoSubTypeRetriever.buildAndLoadOrUploadTo(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
	}
	
	public <T> Class<T> buildPojoSubTypeAndLoadOrUpload(String className, int options, Class<?>... superClasses) {
		return buildPojoSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), className, options, superClasses);
	}
	
	public <T> Class<T> buildPojoSubTypeAndLoadOrUploadTo(ClassLoader classLoader, String className, int options, Class<?>... superClasses) {
		return pojoSubTypeRetriever.buildAndLoadOrUploadTo(classLoader, className, options, superClasses);
	}
	
	public <T> Class<T> buildFunctionSubTypeAndLoadOrUpload(int parametersLength) {
		return buildFunctionSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), parametersLength);
	}
	
	@SuppressWarnings("unchecked")
	public <T> Class<T> buildFunctionSubTypeAndLoadOrUploadTo(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "FunctionFor" + parametersLength +	"Parameters";
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return (Class<T>) buildAndLoadOrUploadTo(
			classLoader,
			sourceCodeHandler.generateFunction(className, parametersLength)
		).get(
			className
		);
	}
	
	public <T> Class<T> buildConsumerSubTypeAndLoadOrUpload(int parametersLength) {
		return buildConsumerSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), parametersLength);
	}
	
	@SuppressWarnings("unchecked")
	public <T> Class<T> buildConsumerSubTypeAndLoadOrUploadTo(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "ConsumerFor" + parametersLength + "Parameters";
		String packageName = MultiParamsConsumer.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return (Class<T>) buildAndLoadOrUploadTo(
			classLoader,
			sourceCodeHandler.generateConsumer(className, parametersLength)
		).get(
			className
		);
	}
	
	public <T> Class<T> buildPredicateSubTypeAndLoadOrUpload(int parametersLength) {
		return buildPredicateSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), parametersLength);
	}
	
	@SuppressWarnings("unchecked")
	public <T> Class<T> buildPredicateSubTypeAndLoadOrUploadTo(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "PredicateFor" + parametersLength + "Parameters";
		String packageName = MultiParamsPredicate.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return (Class<T>) buildAndLoadOrUploadTo(
			classLoader,
			sourceCodeHandler.generatePredicate(className, parametersLength)
		).get(
			className
		);
	}
	
	public <T extends Executor> Class<T> buildCodeExecutorSubTypeAndLoadOrUpload(String className, StatementSourceGenerator statement) {
		return buildCodeExecutorSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), className, statement);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Executor> Class<T> buildCodeExecutorSubTypeAndLoadOrUploadTo(ClassLoader classLoader, String className, StatementSourceGenerator statement) {
		return (Class<T>) buildAndLoadOrUploadTo(
			classLoader,
			sourceCodeHandler.generateExecutor(className, statement)
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
				Class<? extends Executor> executableClass = buildCodeExecutorSubTypeAndLoadOrUploadTo(
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
			return buildAndLoadOrUploadTo(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
		}	
		
		public Class<?> buildAndLoadOrUpload(
			String className,
			int options, 
			Class<?>... superClasses
		) {
			return classFactory.buildAndLoadOrUpload(
				sourceGenerator.create(className, options, superClasses)
			).get(
				className
			);
		}
		
		@SuppressWarnings("unchecked")
		public <T> Class<T> buildAndLoadOrUploadTo(
			ClassLoader classLoader,
			String className,
			int options, 
			Class<?>... superClasses
		) {
			return (Class<T>) classFactory.buildAndLoadOrUploadTo(
				classLoader, 
				sourceGenerator.create(className, options, superClasses)
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
		
	}
	
	public static abstract class ExecuteConfig<C extends ExecuteConfig<C>> {
    	ClassLoader parentClassLoader;
    	boolean useDefaultClassLoaderAsParentIfParentClassLoaderIsNull;
    	List<Object> params;
    	
    	ExecuteConfig() {
    		useDefaultClassLoaderAsParentIfParentClassLoaderIsNull = true;
    	}
    	
		@SuppressWarnings("unchecked")
		public C useAsParentClassLoader(ClassLoader parentClassLoader) {
			this.parentClassLoader = parentClassLoader;
			return (C)this;
		}
		
		@SuppressWarnings("unchecked")
		public C useDefaultClassLoaderAsParent(boolean flag) {
			this.useDefaultClassLoaderAsParentIfParentClassLoaderIsNull = flag;
			return (C)this; 
		}
		
		@SuppressWarnings("unchecked")
		public C withParameter(Object... parameters) {
			if (params == null) {
				params = new ArrayList<>();
			}
			for (Object param : parameters) {
				params.add(param);
			}
			return (C)this;
		}		
		
    	ClassLoader getParentClassLoader() {
			return parentClassLoader;
		}

		boolean isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull() {
			return useDefaultClassLoaderAsParentIfParentClassLoaderIsNull;
		}

		Object[] getParams() {
			return params != null ?
				params.toArray(new Object[params.size()]) : 
				null;
		}
		
		
		
		public static ForProperties forDefaultProperties() {
			return new ForProperties();
		}
		
		public static ForProperties forProperties(Properties properties) {
    		ForProperties fromProperties = new ForProperties();
    		fromProperties.properties = properties;
    		return fromProperties;
    	}
    	
    	public static ForProperties forProperty(String propertyName) {
    		ForProperties fromProperties = new ForProperties();
    		fromProperties.propertyName = propertyName;
    		return fromProperties;
    	}
    	
    	public static ForProperties forPropertiesFile(String filePath) {
    		ForProperties fromProperties = new ForProperties();
    		fromProperties.filePath = filePath;
    		return fromProperties;
    	}
    	
    	public static ForStatementSourceGenerator forStatementSourceGenerator() {
    		return new ForStatementSourceGenerator();
    	}
    	
    	
    	public static class ForProperties extends ExecuteConfig<ForProperties> {
    		private Properties properties;
    		private String propertyName;
    		private String filePath;
    		private boolean isAbsoluteFilePath;
    		private Map<String, String> defaultValues;
    		    		
    		private ForProperties() {
    			isAbsoluteFilePath = false;
    		}
    		
    		
    		public ForProperties setPropertyName(String propertyName) {
    			this.propertyName = propertyName;
    			return this;
    		}
    		
    		public ForProperties setFilePathAsAbsolute() {
    			this.isAbsoluteFilePath = true;
    			return this;
    		}
    		
    		public ForProperties withDefaultPropertyValue(String key, String value) {
    			if (defaultValues == null) {
    				defaultValues = new HashMap<>();
    			}
    			defaultValues.put(key, value);
    			return this;
    		}
    		
    		public ForProperties withDefaultPropertyValues(Map<String, String> defaultValues) {
    			if (defaultValues == null) {
    				defaultValues = new HashMap<>();
    			}
    			defaultValues.putAll(defaultValues);
    			return this;
    		}

			Properties getProperties() {
				return properties;
			}


			String getPropertyName() {
				return propertyName;
			}


			String getFilePath() {
				return filePath;
			}


			boolean isAbsoluteFilePath() {
				return isAbsoluteFilePath;
			}


			Map<String, String> getDefaultValues() {
				return defaultValues;
			}   		
    		
    	}
    	
    	
    	public static class ForStatementSourceGenerator extends ExecuteConfig<ForStatementSourceGenerator> {
    		StatementSourceGenerator statement;
    		
    		private ForStatementSourceGenerator() {
    			this.statement = StatementSourceGenerator.createSimple().setElementPrefix("\t");
    		}

			StatementSourceGenerator getStatement() {
				return statement;
			}
			
			public ForStatementSourceGenerator addCodeRow(String... codeRow) {
				statement.addCodeRow(codeRow);
				return this;
			}
			
			public ForStatementSourceGenerator addCode(String... code) {
				statement.addCode(code);
				return this;
			}
    		
			public ForStatementSourceGenerator addCode(SourceGenerator... generators) {
				statement.addElement(generators);
				return this;
			}

			public ForStatementSourceGenerator useType(Class<?>... classes) {
				statement.useType(classes);
				return this;
			}
    	}
    }
	
	
	public static class LoadOrBuildAndDefineConfig {
		private Collection<String> compilationClassPaths;
		private Collection<String> classPathsForNotFoundClassesDuringCompilantion;
		private Collection<String> classPathsForNotFoundClassesDuringLoading;
		private Collection<UnitSourceGenerator> unitSourceGenerators;
		private ClassLoader classLoader;
		private boolean useOneShotJavaCompiler;
		
		private LoadOrBuildAndDefineConfig(UnitSourceGenerator... unitsCode) {
			unitSourceGenerators = Arrays.asList(unitsCode);
		}
		
		public static LoadOrBuildAndDefineConfig forUnitSourceGeneratoror(UnitSourceGenerator... unitsCode) {
			LoadOrBuildAndDefineConfig config = new LoadOrBuildAndDefineConfig(unitsCode);
			return config;
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig add(UnitSourceGenerator... unitsCode) {
			unitSourceGenerators.addAll(Arrays.asList(unitsCode));
			return this;
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig addCompilationClassPaths(Collection<String>... classPathCollections) {
			if (compilationClassPaths == null) {
				compilationClassPaths = new HashSet<>();
			}
			for (Collection<String> classPathCollection : classPathCollections) {
				compilationClassPaths.addAll(classPathCollection);
			}
			return this;
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig addCompilationClassPaths(String... classPaths) {
			return addCompilationClassPaths(Arrays.asList(classPaths));
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig addClassPathForNotFoundClassesDuringCompilantion(Collection<String>... classPathCollections) {
			if (classPathsForNotFoundClassesDuringCompilantion == null) {
				classPathsForNotFoundClassesDuringCompilantion = new HashSet<>();
			}
			for (Collection<String> classPathCollection : classPathCollections) {
				classPathsForNotFoundClassesDuringCompilantion.addAll(classPathCollection);
			}
			return this;
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig addClassPathForNotFoundClassesDuringCompilantion(String... classPaths) {
			return addClassPathForNotFoundClassesDuringCompilantion(Arrays.asList(classPaths));
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig addClassPathForNotFoundClassesDuringLoading(Collection<String>... classPathCollections) {
			if (classPathsForNotFoundClassesDuringLoading == null) {
				classPathsForNotFoundClassesDuringLoading = new HashSet<>();
			}
			for (Collection<String> classPathCollection : classPathCollections) {
				classPathsForNotFoundClassesDuringLoading.addAll(classPathCollection);
			}
			return this;
		}
		
		@SafeVarargs
		public final LoadOrBuildAndDefineConfig addClassPathForNotFoundClassesDuringLoading(String... classPaths) {
			return addClassPathForNotFoundClassesDuringLoading(Arrays.asList(classPaths));
		}
		
		public LoadOrBuildAndDefineConfig useClassLoader(ClassLoader classLoader) {
			this.classLoader = classLoader;
			return this;
		}
		
		public LoadOrBuildAndDefineConfig useOneShotJavaCompiler(boolean flag) {
			this.useOneShotJavaCompiler = flag;
			return this;
		}

		Collection<String> getCompilationClassPaths() {
			return compilationClassPaths;
		}

		Collection<String> getClassPathsForNotFoundClassesDuringCompilantion() {
			return classPathsForNotFoundClassesDuringCompilantion;
		}

		Collection<String> getClassPathsForNotFoundClassesDuringLoading() {
			return classPathsForNotFoundClassesDuringLoading;
		}

		Collection<UnitSourceGenerator> getUnitSourceGenerators() {
			return unitSourceGenerators;
		}

		ClassLoader getClassLoader() {
			return classLoader;
		}

		boolean isUseOneShotJavaCompiler() {
			return useOneShotJavaCompiler;
		}
		
	}
}
