package org.burningwave.core.assembler;

import java.io.InputStream;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import org.burningwave.core.ManagedLogger.Repository;
import org.burningwave.core.SLF4JManagedLoggerRepository;
import org.burningwave.core.SimpleManagedLoggerRepository;
import org.burningwave.core.iterable.Properties;

public class StaticComponentContainer {
	
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate ByteBufferDelegate;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.reflection.ConstructorHelper ConstructorHelper;
	public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
	public static final org.burningwave.core.reflection.FieldHelper FieldHelper;
	public static final org.burningwave.core.iterable.Properties GlobalProperties;
	public static final org.burningwave.core.jvm.JVMInfo JVMInfo;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
	public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
	public static final org.burningwave.core.classes.MemberFinder MemberFinder;
	public static final org.burningwave.core.reflection.MethodHelper MethodHelper;
	public static final org.burningwave.core.Strings.Paths Paths;
	public static final org.burningwave.core.io.Streams Streams;
	public static final org.burningwave.core.Strings Strings;
	public static final org.burningwave.core.Throwables Throwables;
	
	static {
		Throwables = org.burningwave.core.Throwables.create();
		Map.Entry<org.burningwave.core.iterable.Properties, URL> propBag =
			loadFirstOneFound("burningwave.static.properties", "burningwave.static.default.properties");
		GlobalProperties = propBag.getKey();
		ManagedLoggersRepository = createManagedLoggersRepository(GlobalProperties);
		URL globalPropertiesFileUrl = propBag.getValue();
		if (globalPropertiesFileUrl != null) {
			ManagedLoggersRepository.logInfo(StaticComponentContainer.class, "Building static components by using " + globalPropertiesFileUrl);
		} else {
			ManagedLoggersRepository.logInfo(StaticComponentContainer.class, "Building static components by using configuration");
		}
		ManagedLoggersRepository.logInfo(StaticComponentContainer.class, "Instantiated {}", ManagedLoggersRepository.getClass().getName());
		try {			
			Strings = org.burningwave.core.Strings.create();
			Paths = org.burningwave.core.Strings.Paths.create();
			FileSystemHelper = org.burningwave.core.io.FileSystemHelper.create();
			ByteBufferDelegate = org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate.create();
			Streams = org.burningwave.core.io.Streams.create(GlobalProperties);
			JVMInfo = org.burningwave.core.jvm.JVMInfo.create();
			LowLevelObjectsHandler = org.burningwave.core.jvm.LowLevelObjectsHandler.create();
			Classes = org.burningwave.core.classes.Classes.create();
			Cache = org.burningwave.core.Cache.create(GlobalProperties);
			MemberFinder = org.burningwave.core.classes.MemberFinder.create();
			ConstructorHelper = org.burningwave.core.reflection.ConstructorHelper.create();
			FieldHelper = org.burningwave.core.reflection.FieldHelper.create();
			MethodHelper = org.burningwave.core.reflection.MethodHelper.create();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(StaticComponentContainer.class, "Exception occurred", exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private static org.burningwave.core.ManagedLogger.Repository createManagedLoggersRepository(Properties properties) {
		try {
			String className = (String)GlobalProperties.getProperty(Repository.TYPE_CONFIG_KEY);
			if (className == null || "autodetect".equalsIgnoreCase(className = className.trim())) {
				try {
					Class.forName("org.slf4j.Logger");
					return new SLF4JManagedLoggerRepository(properties);
				} catch (Throwable exc2) {
					return new SimpleManagedLoggerRepository(properties);
				}
			} else {
				return (Repository)Class.forName(className).getConstructor(Properties.class).newInstance(properties);
			}
			
		} catch (Throwable exc) {
			exc.printStackTrace();
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private static Map.Entry<org.burningwave.core.iterable.Properties, URL> loadFirstOneFound(String... fileNames) {
		org.burningwave.core.iterable.Properties properties = new Properties();
		Map.Entry<org.burningwave.core.iterable.Properties, URL> propertiesBag = new AbstractMap.SimpleEntry<>(properties, null);
		for (String fileName : fileNames) {
			ClassLoader classLoader = Optional.ofNullable(StaticComponentContainer.class.getClassLoader()).orElseGet(() ->
				ClassLoader.getSystemClassLoader()
			);
			InputStream propertiesFileIS = classLoader.getResourceAsStream(fileName);
			if (propertiesFileIS != null) {				
				try {
					properties.load(propertiesFileIS);
					URL configFileURL = classLoader.getResource(fileName);
					propertiesBag.setValue(configFileURL);
					break;
				} catch (Throwable exc) {
					exc.printStackTrace();
					throw Throwables.toRuntimeException(exc);
				}
			}
		}
		return propertiesBag;
	}
	
}
