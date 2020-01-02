package org.burningwave.core.assembler;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.CodeGenerator;
import org.burningwave.core.classes.ConstructorHelper;
import org.burningwave.core.classes.FieldHelper;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodHelper;
import org.burningwave.core.classes.CodeGenerator.ForCodeExecutor;
import org.burningwave.core.classes.CodeGenerator.ForConsumer;
import org.burningwave.core.classes.CodeGenerator.ForFunction;
import org.burningwave.core.classes.CodeGenerator.ForPojo;
import org.burningwave.core.classes.CodeGenerator.ForPredicate;
import org.burningwave.core.classes.hunter.ByteCodeHunter;
import org.burningwave.core.classes.hunter.ClassHunter;
import org.burningwave.core.classes.hunter.ClassPathHunter;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.reflection.CallerRetriever;
import org.burningwave.core.reflection.ConsumerBinder;
import org.burningwave.core.reflection.FunctionBinder;
import org.burningwave.core.reflection.FunctionalInterfaceFactory;
import org.burningwave.core.reflection.ObjectRetriever;
import org.burningwave.core.reflection.PropertyAccessor;
import org.burningwave.core.reflection.RunnableBinder;
import org.burningwave.core.reflection.SupplierBinder;

public class ComponentContainer implements ComponentSupplier {
	private static String DEFAULT_CONFIGURATION_FILE_RELATIVE_PATH =  ComponentContainer.class.getPackage().getName().substring(0, ComponentContainer.class.getPackage().getName().lastIndexOf(".")).replaceAll("\\.", "/") + "/config/burningwave.properties";
	
	private Map<Class<? extends Component>, Component> components;
	private String configFileName;
	private Properties config;
	
	ComponentContainer(String fileName) {
		configFileName = fileName;
		components = new ConcurrentHashMap<>();
		config = new Properties();
	}

	protected ComponentContainer init() {
		FileSystemItem defaultConfig = getFileSystemHelper().getResource(DEFAULT_CONFIGURATION_FILE_RELATIVE_PATH);
		try(InputStream inputStream = defaultConfig.toInputStream()) {
			config.load(inputStream);
		} catch (Throwable exc) {
			Throwables.toRuntimeException(exc);
		}
		FileSystemItem customConfig = getFileSystemHelper().getResource(configFileName);
		if (customConfig != null) {
			try(InputStream inputStream = customConfig.toInputStream()) {
				config.load(inputStream);
			} catch (Throwable exc) {
				Throwables.toRuntimeException(exc);
			}
		} else {
			logWarn("Custom configuration file " + configFileName + " not found. Default configuration file, located in path " + defaultConfig.getAbsolutePath()+ ", is assumed.");
		}
		return this;
	}

	public static ComponentContainer getInstance() {
		return LazyHolder.getComponentContainerInstance();
	}
	
	@SuppressWarnings("resource")
	public final static ComponentContainer create(String fileName) {
		return new ComponentContainer(fileName).init();
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
			ObjectRetriever objectRetriever = getObjectRetriever();
			return MemoryClassLoader.create(
				objectRetriever.retrieveFromProperties(
					config,
					MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_CONFIG_KEY,
					MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY,
					MemoryClassLoader.DEFAULT_CONFIG_VALUES,
					ClassLoader.class,
					this
				),
				getClassHelper(),
				objectRetriever
			);
		});
	}

	@Override
	public ClassFactory getClassFactory() {
		return getOrCreate(ClassFactory.class, () -> 
			ClassFactory.create(
				getClassHelper(),
				getClassPathHunter(),
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
			ObjectRetriever objectRetriever = getObjectRetriever();
			return ClassHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemHelper(), 
				getPathHelper(),
				getStreamHelper(),
				getClassFactory(),
				getClassHelper(),
				getMemberFinder(),
				objectRetriever,
				objectRetriever.retrieveFromProperties(config,
					ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY,
					ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY,
					ClassHunter.DEFAULT_CONFIG_VALUES,
					ClassLoader.class,
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
				getMemberFinder(),
				getObjectRetriever()
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
				getMemberFinder(),
				getObjectRetriever()
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
	public ObjectRetriever getObjectRetriever() {
		return getOrCreate(ObjectRetriever.class, () ->
			ObjectRetriever.create(
				() -> getClassHelper(),
				() -> getClassFactory(),
				getStreamHelper(),
				getMethodHelper(),
				getIterableObjectHelper()
			)
		);
	}

	@Override
	public ClassHelper getClassHelper() {
		return getOrCreate(ClassHelper.class, () ->
			ClassHelper.create(
				getMemberFinder(),
				() -> getClassFactory(),
				getObjectRetriever()
				
			)
		);
	}
	
	private void closeComponents() {
		components.forEach((type, instance) -> { 
			instance.close();
			components.remove(type);
		});
		components = null;
	}
	
	@Override
	public void close() {
		if (LazyHolder.getComponentContainerInstance() != this) {
			closeComponents();
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
}
