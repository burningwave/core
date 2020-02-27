package org.burningwave.core.assembler;

import java.io.InputStream;
import java.util.Optional;

import org.burningwave.ManagedLogger.Repository;
import org.burningwave.SLF4JManagedLoggerRepository;
import org.burningwave.SimpleManagedLoggerRepository;
import org.burningwave.core.iterable.Properties;

public class StaticComponentsContainer {
	
	public static org.burningwave.Throwables Throwables;
	public static org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate ByteBufferDelegate;
	public static org.burningwave.core.Cache Cache;
	public static org.burningwave.ManagedLogger.Repository ManagedLoggersRepository;
	public static org.burningwave.core.Strings Strings;
	public static org.burningwave.core.Strings.Paths Paths;
	public static org.burningwave.core.io.Streams Streams;
	public static org.burningwave.core.iterable.Properties GlobalProperties;
	public static org.burningwave.core.jvm.JVMInfo JVMInfo;
	public static org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
	public static org.burningwave.core.classes.Classes Classes;
	public static org.burningwave.core.classes.MemberFinder MemberFinder;
	public static org.burningwave.core.reflection.ConstructorHelper ConstructorHelper;
	public static org.burningwave.core.reflection.FieldHelper FieldHelper;
	public static org.burningwave.core.reflection.MethodHelper MethodHelper;
	
	static {
		try {
			Throwables = org.burningwave.Throwables.create();
			InputStream propertiesFileIS = Optional.ofNullable(StaticComponentsContainer.class.getClassLoader()).orElseGet(() -> ClassLoader.getSystemClassLoader()).getResourceAsStream("burningwave.static.properties");
			GlobalProperties = new Properties();
			if (propertiesFileIS != null) {				
				GlobalProperties.load(propertiesFileIS);
			}
			Strings = org.burningwave.core.Strings.create();
			Paths = org.burningwave.core.Strings.Paths.create();
			ByteBufferDelegate = org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate.create();
			try {
				String className = (String)GlobalProperties.getProperty(Repository.REPOSITORY_TYPE_CONFIG_KEY);
				ManagedLoggersRepository = (Repository)Class.forName(className).getConstructor().newInstance();
			} catch (Throwable exc) {
				try {
					Class.forName("org.slf4j.Logger");
					ManagedLoggersRepository = new SLF4JManagedLoggerRepository();
				} catch (Throwable exc2) {
					ManagedLoggersRepository =  new SimpleManagedLoggerRepository();
				}
			}			
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
	
}
