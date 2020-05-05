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
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.PathHelper;


public class ClassFactory implements Component {
	public static final String DEFAULT_CLASS_LOADER_CONFIG_KEY = "class-factory.default-class-loader";
	public static final String CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY = "class-factory.java-memory-compiler.class-repositories";
	public static final String CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER_CONFIG_KEY = "class-factory.default-class-loader.class-repositories";
	public static final String BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS_CONFIG_KEY = "class-factory.byte-code-hunter.search-config.check-file-options";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	private ClassLoader defaultClassLoader;
	private ByteCodeHunter byteCodeHunter;
	private Supplier<ClassLoader> defaultClassLoaderSupplier;
	private int byteCodeHunterSearchConfigCheckFileOptions;
	
	private ClassFactory(
		ByteCodeHunter byteCodeHunter,
		SourceCodeHandler sourceCodeHandler,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier,
		int byteCodeHunterSearchConfigCheckFileOptions
	) {	
		this.byteCodeHunter = byteCodeHunter;
		this.sourceCodeHandler = sourceCodeHandler;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
		this.defaultClassLoaderSupplier = defaultClassLoaderSupplier;
		this.byteCodeHunterSearchConfigCheckFileOptions = byteCodeHunterSearchConfigCheckFileOptions;
	}
	
	public static ClassFactory create(
		ByteCodeHunter byteCodeHunter,
		SourceCodeHandler sourceCodeHandler,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier,
		Integer byteCodeHunterSearchConfigCheckFileOptions
	) {
		return new ClassFactory(
			byteCodeHunter,
			sourceCodeHandler, 
			javaMemoryCompiler, pathHelper, defaultClassLoaderSupplier, byteCodeHunterSearchConfigCheckFileOptions
		);
	}
	
	private ClassLoader getDefaultClassLoader() {
		return defaultClassLoader != null? defaultClassLoader :
			(defaultClassLoader = defaultClassLoaderSupplier.get());
	}
	
	public Map<String, ByteBuffer> build(Collection<String> mainClassPaths, Collection<String> extraClassPaths, UnitSourceGenerator... unitsCode) {
		return build(mainClassPaths, extraClassPaths, Arrays.asList(unitsCode).stream().map(unitCode -> unitCode.make()).collect(Collectors.toList()));
	}
	
	public Map<String, ByteBuffer> build(Collection<String> mainClassPaths, Collection<String> extraClassPaths, Collection<String> unitsCode) {
		logInfo("Try to compile: \n\n{}\n",String.join("\n", unitsCode));
		return javaMemoryCompiler.compile(
			unitsCode,
			mainClassPaths, 
			extraClassPaths
		);
	}
	
	public Map<String, ByteBuffer> build(Collection<String> mainClassPaths, Collection<String> extraClassPaths, String... unitsCode) {
		logInfo("Try to compile: \n\n{}\n",String.join("\n", unitsCode));
		return javaMemoryCompiler.compile(
			Arrays.asList(unitsCode),
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
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY), 
			pathHelper.getPaths(CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER_CONFIG_KEY), classLoader, unitsCode
		);
	}
	
	public ClassRetriever buildAndLoadOrUploadTo(
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
			AtomicReference<Map<String, ByteBuffer>> extraClassPathsForClassLoaderByteCodesAR = new AtomicReference<>();
			for (String className : classesName) {
				try {
					classes.put(className, classLoader.loadClass(className));
				} catch (Throwable exc) {
					Map<String, ByteBuffer> compiledByteCodes = build(compilationClassPaths, compilationClassPathsForNotFoundClasses, unitsCode);
					return (clsName, additionalByteCodes) -> {
						try {
							Map<String, ByteBuffer> finalByteCodes = compiledByteCodes;
							if (additionalByteCodes != null) {
								finalByteCodes = new HashMap<>(compiledByteCodes);
								finalByteCodes.putAll(additionalByteCodes);
							}
							return ClassLoaders.loadOrUploadByteCode(clsName, finalByteCodes, classLoader);
						} catch (Throwable innExc) {
							if (extraClassPathsForClassLoaderByteCodesAR.get() == null) {
								synchronized (extraClassPathsForClassLoaderByteCodesAR) {
									if (extraClassPathsForClassLoaderByteCodesAR.get() == null) {
										try(ByteCodeHunter.SearchResult result = byteCodeHunter.loadCache(
											SearchConfig.forPaths(
												classLoaderClassPaths
											).deleteFoundItemsOnClose(
												false
											).checkFileOptions(
												byteCodeHunterSearchConfigCheckFileOptions
											).optimizePaths(
												true
											)
										).findBy()) {
											Map<String, ByteBuffer> extraClassPathsForClassLoaderByteCodes = new HashMap<>();
											result.getItemsFoundFlatMap().values().forEach(javaClass -> {
												extraClassPathsForClassLoaderByteCodes.put(javaClass.getName(), javaClass.getByteCode());
											});
											extraClassPathsForClassLoaderByteCodes.putAll(compiledByteCodes);
											extraClassPathsForClassLoaderByteCodesAR.set(extraClassPathsForClassLoaderByteCodes);
										}
									}
								}
							}
							return ThrowingSupplier.get(() -> {
								Map<String, ByteBuffer> finalByteCodes = extraClassPathsForClassLoaderByteCodesAR.get();
								if (additionalByteCodes != null) {
									finalByteCodes = new HashMap<>(extraClassPathsForClassLoaderByteCodesAR.get());
									finalByteCodes.putAll(additionalByteCodes);
								}
								return ClassLoaders.loadOrUploadByteCode(clsName, extraClassPathsForClassLoaderByteCodesAR.get(), classLoader);
							});
						}
					};
					
				}
			}
			logInfo("Classes {} loaded without building by classloader {} ", String.join(", ", classes.keySet()), classLoader);
			return (clsName, additionalByteCodes) -> classes.get(clsName);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
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
	
	public <T extends CodeExecutor> Class<T> buildCodeExecutorSubTypeAndLoadOrUpload(String className, StatementSourceGenerator statement) {
		return buildCodeExecutorSubTypeAndLoadOrUploadTo(getDefaultClassLoader(), className, statement);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends CodeExecutor> Class<T> buildCodeExecutorSubTypeAndLoadOrUploadTo(ClassLoader classLoader, String className, StatementSourceGenerator statement) {
		return (Class<T>) buildAndLoadOrUploadTo(
			classLoader,
			sourceCodeHandler.generateExecutor(className, statement)
		).get(
			className
		);
	}
	
	public <T> T execute(
		StatementSourceGenerator statement,
		Object... parameters
	) {
		return execute(getDefaultClassLoader(), statement, parameters);
	}
	
	public <T> T execute(
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
				String packageName = CodeExecutor.class.getPackage().getName();
				Class<? extends CodeExecutor> executableClass = buildCodeExecutorSubTypeAndLoadOrUploadTo(
					memoryClassLoader, packageName + ".CodeExecutor_" + UUID.randomUUID().toString().replaceAll("-", ""), statement
				);
				CodeExecutor executor = Constructors.newInstanceOf(executableClass);
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
}
