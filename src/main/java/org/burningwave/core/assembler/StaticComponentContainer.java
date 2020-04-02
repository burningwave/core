package org.burningwave.core.assembler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.burningwave.core.function.ThrowingSupplier;

public class StaticComponentContainer {
	private static final String CLEAR_TEMPORARY_FOLDER_ON_INIT_CONFIG_KEY = "static-component-container.clear-temporary-folder-on-init";
	private static final String HIDE_BANNER_ON_INIT_CONFIG_KEY = "static-component-container.hide-banner-on-init";
	
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate ByteBufferDelegate;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
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
		boolean hideBannerOnInit = Boolean.valueOf(GlobalProperties.getProperty(HIDE_BANNER_ON_INIT_CONFIG_KEY));
		if (!hideBannerOnInit) {
			showBanner();
		}
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
			Runtime.getRuntime().addShutdownHook(new Thread(FileSystemHelper::close));
			boolean clearTemporaryFolderOnInit = Boolean.valueOf(GlobalProperties.getProperty(CLEAR_TEMPORARY_FOLDER_ON_INIT_CONFIG_KEY));
			if (clearTemporaryFolderOnInit) {
				FileSystemHelper.clearMainTemporaryFolder();
			}
			ByteBufferDelegate = org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate.create();
			Streams = org.burningwave.core.io.Streams.create(GlobalProperties);
			JVMInfo = org.burningwave.core.jvm.JVMInfo.create();
			LowLevelObjectsHandler = org.burningwave.core.jvm.LowLevelObjectsHandler.create();
			Classes = org.burningwave.core.classes.Classes.create();
			ClassLoaders = org.burningwave.core.classes.Classes.Loaders.create();
			Cache = org.burningwave.core.Cache.create();
			MemberFinder = org.burningwave.core.classes.MemberFinder.create();
			ConstructorHelper = org.burningwave.core.reflection.ConstructorHelper.create();
			FieldHelper = org.burningwave.core.reflection.FieldHelper.create();
			MethodHelper = org.burningwave.core.reflection.MethodHelper.create();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(StaticComponentContainer.class, "Exception occurred", exc);
			throw Throwables.toRuntimeException(exc);
		}
	}

	static void showBanner() {
		String[] banners = getResourceAsStringBuffer("org/burningwave/banner.txt").toString().split("--------------------------------------------------------------------------------------------------");
		System.out.println(banners[new Random().nextInt(banners.length)]);
	}
	
	private static org.burningwave.core.ManagedLogger.Repository createManagedLoggersRepository(
		org.burningwave.core.iterable.Properties properties
	) {
		try {
			String className = (String)GlobalProperties.getProperty(org.burningwave.core.ManagedLogger.Repository.TYPE_CONFIG_KEY);
			if (className == null || "autodetect".equalsIgnoreCase(className = className.trim())) {
				try {
					Class.forName("org.slf4j.Logger");
					return new org.burningwave.core.SLF4JManagedLoggerRepository(properties);
				} catch (Throwable exc2) {
					return new org.burningwave.core.SimpleManagedLoggerRepository(properties);
				}
			} else {
				return (org.burningwave.core.ManagedLogger.Repository)
					Class.forName(className).getConstructor(java.util.Properties.class).newInstance(properties);
			}
			
		} catch (Throwable exc) {
			exc.printStackTrace();
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private static Map.Entry<org.burningwave.core.iterable.Properties, URL> loadFirstOneFound(String... fileNames) {
		org.burningwave.core.iterable.Properties properties = new org.burningwave.core.iterable.Properties();
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
	
	private static StringBuffer getResourceAsStringBuffer(String resourceRelativePath) {
		return ThrowingSupplier.get(() -> {
			ClassLoader classLoader = Optional.ofNullable(StaticComponentContainer.class.getClassLoader()).orElseGet(() ->
				ClassLoader.getSystemClassLoader()
			);
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(
						classLoader.getResourceAsStream(resourceRelativePath)
					)
				)
			) {
				StringBuffer result = new StringBuffer();
				String sCurrentLine;
				while ((sCurrentLine = reader.readLine()) != null) {
					result.append(sCurrentLine + "\n");
				}
				return result;
			}
		});
	}
	
}
