package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Executable;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;


@SuppressWarnings("unchecked")
public class CodeExecutorImpl implements CodeExecutor, Component {
	private ClassFactory classFactory;
	private PathHelper pathHelper;
	private Supplier<ClassFactory> classFactorySupplier;
	private Properties config;
	
	CodeExecutorImpl(
		Supplier<ClassFactory> classFactorySupplier,
		PathHelper pathHelper,
		Properties config
	) {	
		this.classFactorySupplier = classFactorySupplier;
		this.pathHelper = pathHelper;
		this.config = config;
		listenTo(config);
	}
	
	private ClassFactory getClassFactory() {
		return classFactory != null? classFactory :
			(classFactory = classFactorySupplier.get());
	}
	
	@Override
	public <T> T executeProperty(String propertyName, Object... params) {
		return execute(ExecuteConfig.forProperty(propertyName).withParameter(params));
	}
	
	@Override
	public <E extends ExecuteConfig<E>, T> T execute(ExecuteConfig.ForProperties config) {
		java.util.Properties properties = config.getProperties();
		if (properties == null) {
			if (config.getFilePath() == null) {
				properties = this.config; 
			} else {
				Properties tempProperties = new Properties();
				if (config.isAbsoluteFilePath()) {
					Executor.run(() -> 
						tempProperties.load(FileSystemItem.ofPath(config.getFilePath()).toInputStream())
					);
				} else {
					Executor.run(() ->
						tempProperties.load(pathHelper.getResourceAsStream(config.getFilePath()))
					);
				}
				properties = tempProperties;
			}
			
		}
		
		BodySourceGenerator body = config.getBody();
		if (config.getParams() != null && config.getParams().length > 0) {
			for (Object param : config.getParams()) {
				if (param != null) {
					body.useType(param.getClass());
				}
			}
		}
		String importFromConfig = retrieveValue(
			config,
			properties,
			Configuration.Key.PROPERTIES_FILE_IMPORTS_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_EXECUTOR_IMPORTS_SUFFIX
		);
		if (Strings.isNotEmpty(importFromConfig)) {
			Arrays.stream(importFromConfig.replaceAll(";{2,}", ";").split(";")).forEach(imp -> {
				if (Strings.isNotEmpty(imp)) {
					body.useType(imp);
				}
			});
		}
		String executorName = retrieveValue(
			config,
			properties,
			Configuration.Key.PROPERTIES_FILE_CLASS_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_EXECUTOR_NAME_SUFFIX
		);
		String executorSimpleName = retrieveValue(
			config,
			properties,
			Configuration.Key.PROPERTIES_FILE_CLASS_SIMPLE_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_SUPPLIER_SIMPLE_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_EXECUTOR_SIMPLE_NAME_SUFFIX
		);
		if (Strings.isNotEmpty(executorName)) {
			config.setName(executorName);
		} else if (Strings.isNotEmpty(executorSimpleName)) {
			config.setSimpleName(executorSimpleName);
		}
		String code = IterableObjectHelper.resolveStringValue(
			properties,
			config.getPropertyName(), null,
			Configuration.Value.CODE_LINE_SEPARATOR,
			true, config.getDefaultValues()
		);
		if (code.contains(";")) {
			if (config.isIndentCodeActive()) {
				code = code.replaceAll(";{2,}", ";");
				for (String codeLine : code.split(";")) {
					if (Strings.isNotEmpty(codeLine)) {
						body.addCodeLine(codeLine + ";");
					}
				}
			} else {
				body.addCodeLine(code);
			}
			if (!code.contains("return")) {
				body.addCodeLine("return null;");
			}
		} else {
			body.addCodeLine(code.contains("return")?
				code:
				"return " + code + ";"
			);
		}

		return execute(
			(E)config
		);
	}

	private String retrieveValue(ExecuteConfig.ForProperties config, java.util.Properties properties, String... suffixes) {
		for (String suffix : suffixes) {
			String value = IterableObjectHelper.resolveStringValue(
				properties, 
				config.getPropertyName() + suffix,
				null,
				Configuration.Value.CODE_LINE_SEPARATOR,
				true,
				config.getDefaultValues()
			);
			if (value != null) {
				return value;
			}
		}
		return null;
	}		
	
	@Override
	public <E extends ExecuteConfig<E>, T> T execute(BodySourceGenerator body) {
		return execute((E)ExecuteConfig.forBodySourceGenerator(body));
	}
	
	@Override
	public <E extends ExecuteConfig<E>, T> T execute(
		E config
	) {	
		Object executeClient = new Object() {};
		ClassLoader defaultClassLoader = null;
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = defaultClassLoader = ((ClassFactoryImpl)getClassFactory()).getDefaultClassLoader(executeClient);
		}
		if (config.getClassLoader() == null) {
			MemoryClassLoader memoryClassLoader = MemoryClassLoader.create(
				parentClassLoader
			);
			try {
				memoryClassLoader.register(executeClient);
				Class<? extends Executable> executableClass = loadOrBuildAndDefineExecutorSubType(
					config.useClassLoader(memoryClassLoader)
				);
				Executable executor = Constructors.newInstanceDirectOf(executableClass);
				T retrievedElement = executor.executeAndCast(config.getParams());
				return retrievedElement;
			} catch (Throwable exc) {
				return Throwables.throwException(exc);
			} finally {
				if (defaultClassLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)defaultClassLoader).unregister(executeClient, true);
				}
				memoryClassLoader.unregister(executeClient, true);
			}
		} else {
			Function<Boolean, ClassLoader> parentClassLoaderRestorer = null;
			try {
				if (parentClassLoader != null) {
					parentClassLoaderRestorer = ClassLoaders.setAsParent(config.getClassLoader(), parentClassLoader);
				}
				Class<? extends Executable> executableClass = loadOrBuildAndDefineExecutorSubType(
					config
				);
				Executable executor = Constructors.newInstanceDirectOf(executableClass);
				T retrievedElement = executor.executeAndCast(config.getParams());
				if (parentClassLoaderRestorer != null) {
					parentClassLoaderRestorer.apply(true);
				}
				return retrievedElement;
			} catch (Throwable exc) {
				return Throwables.throwException(exc);
			} finally {
				if (defaultClassLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)defaultClassLoader).unregister(executeClient, true);
				}
			}
		}
	}
	
	@Override
	public <E extends LoadOrBuildAndDefineConfig.ForCodeExecutorAbst<E>, T extends Executable> Class<T> loadOrBuildAndDefineExecutorSubType(
		E config
	) {	
		ClassFactory.ClassRetriever classRetriever = getClassFactory().loadOrBuildAndDefine(
			config
		);
		Class<T> executableClass = (Class<T>) classRetriever.get(
			config.getExecutorName()
		);
		classRetriever.close();
		return executableClass;
	}
	
	@Override
	public void close() {
		unregister(config);
		classFactory = null;
		pathHelper = null;
		classFactorySupplier = null;
		config = null;
	}
}