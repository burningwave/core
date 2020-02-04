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
package org.burningwave.core.assembler;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.CodeGenerator;
import org.burningwave.core.classes.CodeGenerator.ForCodeExecutor;
import org.burningwave.core.classes.CodeGenerator.ForConsumer;
import org.burningwave.core.classes.CodeGenerator.ForFunction;
import org.burningwave.core.classes.CodeGenerator.ForPojo;
import org.burningwave.core.classes.CodeGenerator.ForPredicate;
import org.burningwave.core.classes.ConstructorHelper;
import org.burningwave.core.classes.FieldHelper;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodHelper;
import org.burningwave.core.classes.hunter.ByteCodeHunter;
import org.burningwave.core.classes.hunter.ClassHunter;
import org.burningwave.core.classes.hunter.ClassPathHunter;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.jvm.JVMChecker;
import org.burningwave.core.jvm.LowLevelObjectsHandler;
import org.burningwave.core.reflection.CallerRetriever;
import org.burningwave.core.reflection.ConsumerBinder;
import org.burningwave.core.reflection.FunctionBinder;
import org.burningwave.core.reflection.FunctionalInterfaceFactory;
import org.burningwave.core.reflection.PropertyAccessor;
import org.burningwave.core.reflection.RunnableBinder;
import org.burningwave.core.reflection.SupplierBinder;

public class ComponentContainer implements ComponentSupplier {
	protected Map<Class<? extends Component>, Component> components;
	private String configFileName;
	private Properties config;
	private Thread initializerTask;
	
	ComponentContainer(String fileName) {
		configFileName = fileName;
		components = new ConcurrentHashMap<>();
		config = new Properties();
	}
	
	public final static ComponentContainer create(String fileName) {
		try {
			ComponentContainer componentContainer = new ComponentContainer(fileName);
			componentContainer.launchInit();
			return componentContainer;
		} catch (Throwable exc){
			ManagedLogger.Repository.getInstance().logError(ComponentContainer.class, "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			throw Throwables.toRuntimeException(exc);
		}
	}

	private ComponentContainer init() {
		config.put(PathHelper.CLASSPATHS_PREFIX + ClassFactory.CLASS_REPOSITORIES, "${classPaths}");
		config.put(MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY, "Thread.currentThread().getContextClassLoader()");
		config.put(ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "componentSupplier.getMemoryClassLoader()");
		
		try (Initializer initializer = new Initializer(configFileName)) {	
			FileSystemHelper fileSystemHelper = initializer.getFileSystemHelper();
			FileSystemItem customConfig = fileSystemHelper.getResource(configFileName);
			if (customConfig != null) {
				try(InputStream inputStream = customConfig.toInputStream()) {
					config.load(inputStream);
				} catch (Throwable exc) {
					Throwables.toRuntimeException(exc);
				}
			} else {
				logInfo("Custom configuration file burningwave.properties not found.");
			}
			logInfo(
				"Configuration values:\n\n{}\n\n... Are assumed",
				config.entrySet().stream().map(entry -> "\t" + entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\n"))
			);
			return this;
		}
	}
	
	private ComponentContainer launchInit() {
		initializerTask = new Thread(() -> {
			init();
			synchronized (components) {
				initializerTask = null;
				components.notifyAll();
			}
		});
		initializerTask.start();
		return this;
	}
	
	public void reInit() {
		clear();
		launchInit();
	}
	
	protected void waitForInitializationEnding() {
		if (initializerTask != null) {
			synchronized (components) {
				if (initializerTask != null) {
					try {
						components.wait();
					} catch (InterruptedException exc) {
						ManagedLogger.Repository.getInstance().logError(ComponentContainer.class, "Exception while waiting " + ComponentContainer.class.getSimpleName() + " initializaziont", exc);
						throw Throwables.toRuntimeException(exc);
					}
				}
			}
		}
	}
	
	public static ComponentSupplier getInstance() {
		return LazyHolder.getComponentContainerInstance();
	}
	
	@SuppressWarnings("unchecked")
	public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier) {
		T component = (T)components.get(componentType);
		if (component == null) {	
			waitForInitializationEnding();
			synchronized (components.toString() + "_" + componentType.getName()) {
				if ((component = (T)components.get(componentType)) == null) {
					component = componentSupplier.get();
					components.put(componentType, component);
				}				
			}
		}
		return component;
	}

	@Override
	public ConstructorHelper getConstructorHelper() {
		return getOrCreate(ConstructorHelper.class, () -> 
			ConstructorHelper.create(
				getMemberFinder()
			)
		);
	}

	@Override
	public MethodHelper getMethodHelper() {
		return getOrCreate(MethodHelper.class, () ->
			MethodHelper.create(
				getMemberFinder()
			)
		);
	}

	@Override
	public FieldHelper getFieldHelper() {
		return getOrCreate(FieldHelper.class, () ->
			FieldHelper.create(
				getMemberFinder()
			)
		);
	}

	@Override
	public MemberFinder getMemberFinder() {
		return getOrCreate(MemberFinder.class, ()->
			MemberFinder.create()
		);
	}

	@Override
	public MemoryClassLoader getMemoryClassLoader() {
		return getOrCreate(MemoryClassLoader.class, () -> {
			return MemoryClassLoader.create(
				getLowLevelObjectsHandler().retrieveFromProperties(
					config,
					MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY,
					MemoryClassLoader.DEFAULT_CONFIG_VALUES,
					this
				),
				getClassHelper()
			);
		});
	}

	@Override
	public ClassFactory getClassFactory() {
		return getOrCreate(ClassFactory.class, () -> 
			ClassFactory.create(
				getClassHelper(),
				() -> getMemoryClassLoader(),
				getJavaMemoryCompiler(),
				getPathHelper(),
				getCodeGeneratorForPojo(),
				getCodeGeneratorForFunction(),
				getCodeGeneratorForConsumer(),
				getCodeGeneratorForPredicate(),
				getCodeGeneratorForCodeExecutor()
			)
		);	
	}

	@Override
	public JavaMemoryCompiler getJavaMemoryCompiler() {
		return getOrCreate(JavaMemoryCompiler.class, () ->
			JavaMemoryCompiler.create(
				getFileSystemHelper(),
				getPathHelper(),
				getClassHelper(),
				getClassPathHunter()
			)
		);
	}
	
	@Override
	public ForCodeExecutor getCodeGeneratorForCodeExecutor() {
		return getOrCreate(CodeGenerator.ForCodeExecutor.class, () ->
			CodeGenerator.ForCodeExecutor.create(
				getMemberFinder(),
				getStreamHelper()
			)
		);
	}
	
	@Override
	public ForConsumer getCodeGeneratorForConsumer() {
		return getOrCreate(CodeGenerator.ForConsumer.class, () ->
			CodeGenerator.ForConsumer.create(
				getMemberFinder(),
				getStreamHelper()
			)
		);
	}

	@Override
	public ForFunction getCodeGeneratorForFunction() {
		return getOrCreate(CodeGenerator.ForFunction.class, () ->
			CodeGenerator.ForFunction.create(
				getMemberFinder(),
				getStreamHelper()
			)
		);
	}
	
	@Override
	public ForPredicate getCodeGeneratorForPredicate() {
		return getOrCreate(CodeGenerator.ForPredicate.class, () -> 
			CodeGenerator.ForPredicate.create(
				getMemberFinder(),
				getStreamHelper()
			)
		);
	}
	
	@Override
	public ForPojo getCodeGeneratorForPojo() {
		return getOrCreate(CodeGenerator.ForPojo.class, () -> 
			CodeGenerator.ForPojo.create(
				getMemberFinder(),
				getStreamHelper()
			)
		);
	}

	@Override
	public ClassHunter getClassHunter() {
		return getOrCreate(ClassHunter.class, () -> {
			return ClassHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemHelper(), 
				getPathHelper(),
				getStreamHelper(),
				getClassHelper(),
				getMemberFinder(),
				getLowLevelObjectsHandler().retrieveFromProperties(
					config,
					ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY,
					ClassHunter.DEFAULT_CONFIG_VALUES,
					this
				)
			);
		});
	}

	
	@Override
	public ClassPathHunter getClassPathHunter() {
		return getOrCreate(ClassPathHunter.class, () -> 
			ClassPathHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemHelper(), 
				getPathHelper(),
				getStreamHelper(),
				getClassHelper(),
				getMemberFinder()
			)
		);
	}
	
	@Override
	public ByteCodeHunter getByteCodeHunter() {
		return getOrCreate(ByteCodeHunter.class, () -> 
			ByteCodeHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemHelper(), 
				getPathHelper(),
				getStreamHelper(),
				getClassHelper(),
				getMemberFinder()
			)
		);
	}

	@Override
	public PropertyAccessor.ByFieldOrByMethod getByFieldOrByMethodPropertyAccessor() {
		return getOrCreate(PropertyAccessor.ByFieldOrByMethod.class, () ->  
			PropertyAccessor.ByFieldOrByMethod.create(
				getMemberFinder(),
				getMethodHelper(),
				getFieldHelper()
			)
		);
	}
	
	@Override
	public PropertyAccessor.ByMethodOrByField getByMethodOrByFieldPropertyAccessor() {
		return getOrCreate(PropertyAccessor.ByMethodOrByField.class, () ->  
			PropertyAccessor.ByMethodOrByField.create(
				getMemberFinder(),
				getMethodHelper(),
				getFieldHelper()
			)
		);
	}

	@Override
	public RunnableBinder getRunnableBinder() {
		return getOrCreate(RunnableBinder.class, () ->
			RunnableBinder.create(
				getMemberFinder()
			)
		);
	}

	@Override
	public SupplierBinder getSupplierBinder() {
		return getOrCreate(SupplierBinder.class, () -> 
			SupplierBinder.create(
				getMemberFinder()
			)
		);
	}

	@Override
	public ConsumerBinder getConsumerBinder() {
		return getOrCreate(ConsumerBinder.class, () -> 
			ConsumerBinder.create(
				getMemberFinder(),
				getLambdaCallerRetriever(),
				getClassFactory()::getOrBuildConsumerSubType
			)
		);
	}

	@Override
	public FunctionBinder getFunctionBinder() {
		return getOrCreate(FunctionBinder.class, () ->
			FunctionBinder.create(
				getMemberFinder(),
				getLambdaCallerRetriever(),
				getClassFactory()::getOrBuildFunctionSubType
			)
		);
	}

	@Override
	public FunctionalInterfaceFactory getFunctionalInterfaceFactory() {
		return getOrCreate(FunctionalInterfaceFactory.class, () -> 
			FunctionalInterfaceFactory.create(
				getRunnableBinder(), getSupplierBinder(),
				getConsumerBinder(), getFunctionBinder()
			)
		);
	}

	@Override
	public CallerRetriever getLambdaCallerRetriever() {
		return getOrCreate(CallerRetriever.class, () -> 
			CallerRetriever.create(
				getMemberFinder(), CodeGenerator.CALLER_RETRIEVER_METHOD_NAME
			)
		);
	}
	
	@Override
	public PathHelper getPathHelper() {
		return getOrCreate(PathHelper.class, () ->
			PathHelper.create(
				() -> getFileSystemHelper(),
				getIterableObjectHelper(),
				config
			)
		);
	}
	
	public StreamHelper getStreamHelper() {
		return getOrCreate(StreamHelper.class, () -> 
			StreamHelper.create(
				getFileSystemHelper()
			)
		);
	}
	
	public FileSystemHelper getFileSystemHelper() {
		return getOrCreate(FileSystemHelper.class, () -> 
			FileSystemHelper.create(
				() -> getPathHelper()
			)
		);
	}

	@Override
	public ConcurrentHelper getConcurrentHelper() {
		return getOrCreate(ConcurrentHelper.class, ConcurrentHelper::create);
	}

	@Override
	public IterableObjectHelper getIterableObjectHelper() {
		return getOrCreate(IterableObjectHelper.class, () ->
			IterableObjectHelper.create(
				getByFieldOrByMethodPropertyAccessor()
			)
		);
	}
	
	@Override
	public JVMChecker getJVMChecker() {
		return getOrCreate(JVMChecker.class, () ->
			JVMChecker.create()
		);
	}
	
	@Override
	public LowLevelObjectsHandler getLowLevelObjectsHandler() {
		return getOrCreate(LowLevelObjectsHandler.class, () ->
			LowLevelObjectsHandler.create(
				getJVMChecker(),
				getStreamHelper(),
				() -> getClassFactory(),
				() -> getClassHelper(),
				getMemberFinder(),
				getIterableObjectHelper()
			)
		);
	}

	@Override
	public ClassHelper getClassHelper() {
		return getOrCreate(ClassHelper.class, () ->
			ClassHelper.create(
				() -> getClassFactory(),
				getLowLevelObjectsHandler()				
			)
		);
	}
	
	public ComponentSupplier clear() {
		components.forEach((type, instance) -> { 
			try {
				instance.close();
			} catch (Throwable exc) {
				logError("Exception occurred while closing " + instance, exc);
			}
			components.remove(type);
		});
		return this;
	}
	
	@Override
	public void close() {
		if (LazyHolder.getComponentContainerInstance() != this) {
			clear();
			components = null;
			config.clear();
			config = null;			
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance " + LazyHolder.COMPONENT_CONTAINER_INSTANCE);
		}
	}
	
	private static class LazyHolder {
		private static final ComponentContainer COMPONENT_CONTAINER_INSTANCE = ComponentContainer.create("burningwave.properties");
		
		private static ComponentContainer getComponentContainerInstance() {
			return COMPONENT_CONTAINER_INSTANCE;
		}
	}
	
	public static class Initializer extends ComponentContainer {

			Initializer(String fileName) {
				super(fileName);
			}

			@SuppressWarnings("unchecked")
			public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier) {
				T component = (T)components.get(componentType);
				if (component == null) {
					synchronized (components) {
						if ((component = (T)components.get(componentType)) == null) {
							component = componentSupplier.get();
							components.put(componentType, component);
						}				
					}
				}
				return component;
			}

	}
}
