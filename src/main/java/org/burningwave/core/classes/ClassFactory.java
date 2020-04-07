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
import static org.burningwave.core.assembler.StaticComponentContainer.ConstructorHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
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
	public static final String CLASS_REPOSITORIES = "class-factory.class-repositories";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	private ClassLoader defaultClassLoader;
	private Supplier<ClassLoader> defaultClassLoaderSupplier;
	
	private ClassFactory(
		SourceCodeHandler sourceCodeHandler,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier
	) {	
		this.sourceCodeHandler = sourceCodeHandler;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
		this.defaultClassLoaderSupplier = defaultClassLoaderSupplier;
	}
	
	public static ClassFactory create(
		SourceCodeHandler sourceCodeHandler,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		Supplier<ClassLoader> defaultClassLoaderSupplier
	) {
		return new ClassFactory(
			sourceCodeHandler, 
			javaMemoryCompiler, pathHelper, defaultClassLoaderSupplier
		);
	}
	
	private ClassLoader getDefaultClassLoader() {
		return defaultClassLoader != null? defaultClassLoader :
			(defaultClassLoader = defaultClassLoaderSupplier.get());
	}
	
	public Map<String, ByteBuffer> build(UnitSourceGenerator... unitsCode) {
		return build(Arrays.asList(unitsCode).stream().map(unitCode -> unitCode.make()).collect(Collectors.toList()));
	}
	
	public Map<String, ByteBuffer> build(Collection<String> unitsCode) {
		logInfo("Try to compile: \n\n{}\n",String.join("\n", unitsCode));
		return javaMemoryCompiler.compile(
			unitsCode, 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	public Map<String, ByteBuffer> build(String... unitsCode) {
		logInfo("Try to compile: \n\n{}\n",String.join("\n", unitsCode));
		return javaMemoryCompiler.compile(
			Arrays.asList(unitsCode), 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	public Map<String, Class<?>> buildAndLoadOrUpload(UnitSourceGenerator... unitsCode) {
		return buildAndLoadOrUploadTo(getDefaultClassLoader(), unitsCode);
	}
	
	public Map<String, Class<?>> buildAndLoadOrUploadTo(ClassLoader classLoader, UnitSourceGenerator... unitsCode) {
		try {
			Arrays.stream(unitsCode).forEach(unitCode -> 
				unitCode.getAllClasses().values().forEach(cls -> {
					cls.addConcretizedType(TypeDeclarationSourceGenerator.create(Virtual.class));
				})
			);
			return ClassLoaders.loadOrUploadClasses(build(unitsCode), classLoader);
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
				CodeExecutor executor = ConstructorHelper.newInstanceOf(executableClass);
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
}
