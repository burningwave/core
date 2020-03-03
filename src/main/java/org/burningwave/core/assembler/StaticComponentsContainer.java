package org.burningwave.core.assembler;

import java.io.InputStream;
import java.util.Optional;

import org.burningwave.core.SLF4JManagedLoggerRepository;
import org.burningwave.core.SimpleManagedLoggerRepository;
import org.burningwave.core.ManagedLogger.Repository;
import org.burningwave.core.iterable.Properties;

public class StaticComponentsContainer {
	
	public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
	public static final org.burningwave.core.Throwables Throwables;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate ByteBufferDelegate;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.Strings Strings;
	public static final org.burningwave.core.Strings.Paths Paths;
	public static final org.burningwave.core.io.Streams Streams;
	public static final org.burningwave.core.iterable.Properties GlobalProperties;
	public static final org.burningwave.core.jvm.JVMInfo JVMInfo;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.classes.MemberFinder MemberFinder;
	public static final org.burningwave.core.reflection.ConstructorHelper ConstructorHelper;
	public static final org.burningwave.core.reflection.FieldHelper FieldHelper;
	public static final org.burningwave.core.reflection.MethodHelper MethodHelper;
	
	static {
		Throwables = org.burningwave.core.Throwables.create();
		GlobalProperties = loadGlobalProperties("burningwave.static.properties");
		ManagedLoggersRepository = createManagedLoggersRepository(GlobalProperties);
		try {			
			Strings = org.burningwave.core.Strings.create();
			Paths = org.burningwave.core.Strings.Paths.create();
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
			ManagedLoggersRepository.logError(StaticComponentsContainer.class, "Exception occurred", exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private static org.burningwave.core.iterable.Properties loadGlobalProperties(String fileName) {
		InputStream propertiesFileIS = Optional.ofNullable(StaticComponentsContainer.class.getClassLoader()).orElseGet(() -> ClassLoader.getSystemClassLoader()).getResourceAsStream(fileName);
		org.burningwave.core.iterable.Properties properties = new Properties();
		if (propertiesFileIS != null) {				
			try {
				properties.load(propertiesFileIS);
			} catch (Throwable exc) {
				exc.printStackTrace();
				throw Throwables.toRuntimeException(exc);
			}
		}
		return properties;
	}
	
	private static org.burningwave.core.ManagedLogger.Repository createManagedLoggersRepository(Properties properties) {
		org.burningwave.core.ManagedLogger.Repository repository = null;
		try {
			String className = (String)GlobalProperties.getProperty(Repository.REPOSITORY_TYPE_CONFIG_KEY);
			repository = (Repository)Class.forName(className).getConstructor().newInstance();
		} catch (Throwable exc) {
			try {
				Class.forName("org.slf4j.Logger");
				repository = new SLF4JManagedLoggerRepository();
			} catch (Throwable exc2) {
				repository = new SimpleManagedLoggerRepository();
			}
		}
		String disabledFlag = (String)GlobalProperties.getProperty(Repository.REPOSITORY_DISABLED_FLAG_CONFIG_KEY);
		if (disabledFlag != null && Boolean.parseBoolean(disabledFlag)) {
			repository.disableLogging();
		}
		return repository;
	}
	
}
