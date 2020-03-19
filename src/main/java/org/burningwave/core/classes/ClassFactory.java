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
	
	public Map<String, ByteBuffer> build(Collection<String> unitsCode) {
		return javaMemoryCompiler.compile(
			unitsCode, 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	public Map<String, ByteBuffer> build(String unitCode) {
		logInfo("Try to compile unit code:\n\n" + unitCode +"\n");
		return javaMemoryCompiler.compile(
			Arrays.asList(unitCode), 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	public Class<?> buildAndUploadTo(ClassLoader classLoader, String unitCode) {
		try {
			String className = sourceCodeHandler.extractClassName(unitCode);
			Map<String, ByteBuffer> compiledFiles = build(unitCode);
			logInfo("Class " + className + " succesfully created");
			ClassLoaders.loadOrUploadClasses(compiledFiles, classLoader);
			return classLoader.loadClass(className);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public Class<?> getOrBuild(String className, UnitSourceGenerator unitCode) {
		return getOrBuild(getDefaultClassLoader(), className, unitCode);
	}
	
	public Class<?> getOrBuild(ClassLoader classLoader, String className, UnitSourceGenerator unitCode) {
		return getOrBuild(classLoader, className, () -> unitCode);
	}	
	
	private Class<?> getOrBuild(String className, Supplier<UnitSourceGenerator> unitCode) {
		return getOrBuild(getDefaultClassLoader(), className, unitCode);
	}
	
	private Class<?> getOrBuild(ClassLoader classLoader, String className, Supplier<UnitSourceGenerator> unitCode) {
		Class<?> toRet = ClassLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(classLoader, className, unitCode);
		} else {
			logInfo("Class " + className + " succesfully retrieved");
		}
		return toRet;
	}	
	
	private Class<?> buildAndUploadTo(ClassLoader classLoader, String className, Supplier<UnitSourceGenerator> unitCodeSupplier) {
		try {
			UnitSourceGenerator unit = unitCodeSupplier.get();
			unit.getAllClasses().values().forEach(cls -> {
				cls.addConcretizedType(TypeDeclarationSourceGenerator.create(Virtual.class));
			});
			Map<String, ByteBuffer> compiledFiles = build(unit.make());
			logInfo("Class " + className + " succesfully created");
			ClassLoaders.loadOrUploadClasses(compiledFiles, classLoader);
			return classLoader.loadClass(className);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}

	public Class<?> getOrBuild(String classCode) {
		return getOrBuild(getDefaultClassLoader(), classCode);
	}
	
	public Class<?> getOrBuild(ClassLoader classLoader, String classCode) {
		String className = sourceCodeHandler.extractClassName(classCode);
		java.lang.Class<?> toRet = ClassLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(classLoader, classCode);
		}
		return toRet;
	}	
	
	public PojoSubTypeRetriever createPojoSubTypeRetriever(PojoSourceGenerator sourceGenerator) {
		return PojoSubTypeRetriever.create(this, sourceGenerator);
	}
	
	public Class<?> getOrBuildPojoSubType(String className, Class<?>... superClasses) {
		return getOrBuildPojoSubType(getDefaultClassLoader(), className, superClasses);
	}
	
	public Class<?> getOrBuildPojoSubType(ClassLoader classLoader, String className, Class<?>... superClasses) {
		return pojoSubTypeRetriever.getOrBuild(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
	}
	
	public Class<?> getOrBuildPojoSubType(String className, int options, Class<?>... superClasses) {
		return getOrBuildPojoSubType(getDefaultClassLoader(), className, options, superClasses);
	}
	
	public Class<?> getOrBuildPojoSubType(ClassLoader classLoader, String className, int options, Class<?>... superClasses) {
		return pojoSubTypeRetriever.getOrBuild(classLoader, className, options, superClasses);
	}
	
	public Class<?> getOrBuildFunctionSubType(int parametersLength) {
		return getOrBuildFunctionSubType(getDefaultClassLoader(), parametersLength);
	}
	
	public Class<?> getOrBuildFunctionSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "FunctionFor" + parametersLength +	"Parameters";
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			classLoader,
			className,
			() -> sourceCodeHandler.generateFunction(className, parametersLength)
		);
	}
	
	public Class<?> getOrBuildConsumerSubType(int parametersLength) {
		return getOrBuildConsumerSubType(getDefaultClassLoader(), parametersLength);
	}
	
	public Class<?> getOrBuildConsumerSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "ConsumerFor" + parametersLength + "Parameters";
		String packageName = MultiParamsConsumer.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			classLoader,
			className,
			() -> sourceCodeHandler.generateConsumer(className, parametersLength)
		);
	}
	
	public Class<?> getOrBuildPredicateSubType(int parametersLength) {
		return getOrBuildPredicateSubType(getDefaultClassLoader(), parametersLength);
	}
	
	public Class<?> getOrBuildPredicateSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "PredicateFor" + parametersLength + "Parameters";
		String packageName = MultiParamsPredicate.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			classLoader,
			className,
			() -> sourceCodeHandler.generatePredicate(className, parametersLength)
		);
	}
	
	public Class<?> getOrBuildCodeExecutorSubType(String className, StatementSourceGenerator statement) {
		return getOrBuildCodeExecutorSubType(getDefaultClassLoader(), className, statement);
	}
	
	public Class<?> getOrBuildCodeExecutorSubType(ClassLoader classLoader, String className, StatementSourceGenerator statement) {
		return getOrBuild(
			classLoader,
			className,
			() -> sourceCodeHandler.generateExecutor(className, statement)
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
				Class<?> executableClass = getOrBuildCodeExecutorSubType(
					memoryClassLoader, packageName + ".CodeExecutor_" + UUID.randomUUID().toString().replaceAll("-", ""), statement
				);
				CodeExecutor executor = (CodeExecutor)ConstructorHelper.newInstanceOf(executableClass);
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
		
		public Class<?> getOrBuild(
				ClassLoader classLoader,
			String className,
			Class<?>... superClasses
		) {
			return getOrBuild(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
		}	
		
		public Class<?> getOrBuild(
			String className,
			int options, 
			Class<?>... superClasses
		) {
			return classFactory.getOrBuild(
				className,
				() -> sourceGenerator.make(className, options, superClasses)
			);
		}
		
		public Class<?> getOrBuild(
			ClassLoader classLoader,
			String className,
			int options, 
			Class<?>... superClasses
		) {
			return classFactory.getOrBuild(
				classLoader, 
				className,
				() -> sourceGenerator.make(className, options, superClasses)
			);
		}
	}
}
