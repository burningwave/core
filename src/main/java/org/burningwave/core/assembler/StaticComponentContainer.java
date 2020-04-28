package org.burningwave.core.assembler;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.burningwave.core.function.ThrowingSupplier;

public class StaticComponentContainer {
	private static final String CLEAR_TEMPORARY_FOLDER_ON_INIT_CONFIG_KEY = "static-component-container.clear-temporary-folder-on-init";
	private static final String HIDE_BANNER_ON_INIT_CONFIG_KEY = "static-component-container.hide-banner-on-init";
	
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate ByteBufferDelegate;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
	public static final org.burningwave.core.reflection.Constructors Constructors;
	public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
	public static final org.burningwave.core.reflection.Fields Fields;
	public static final org.burningwave.core.iterable.Properties GlobalProperties;
	public static final org.burningwave.core.jvm.JVMInfo JVMInfo;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
	public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
	public static final org.burningwave.core.classes.Members Members;
	public static final org.burningwave.core.reflection.Methods Methods;
	public static final org.burningwave.core.Strings.Paths Paths;
	public static final org.burningwave.core.io.Resources Resources;
	public static final org.burningwave.core.io.Streams Streams;
	public static final org.burningwave.core.Strings Strings;
	public static final org.burningwave.core.Throwables Throwables;
	
	static {
		Throwables = org.burningwave.core.Throwables.create();
		Resources = new org.burningwave.core.io.Resources();
		Map.Entry<org.burningwave.core.iterable.Properties, URL> propBag =
			Resources.loadFirstOneFound("burningwave.static.properties", "burningwave.static.default.properties");
		GlobalProperties = propBag.getKey();
		if (!Boolean.valueOf(GlobalProperties.getProperty(HIDE_BANNER_ON_INIT_CONFIG_KEY))) {
			showBanner();
		}
		ManagedLoggersRepository = createManagedLoggersRepository(GlobalProperties);
		URL globalPropertiesFileUrl = propBag.getValue();
		if (globalPropertiesFileUrl != null) {
			ManagedLoggersRepository.logInfo(
				StaticComponentContainer.class, "Building static components by using " + ThrowingSupplier.get(() ->
					URLDecoder.decode(
						globalPropertiesFileUrl.toString(), StandardCharsets.UTF_8.name()
					)
				)
			);
		} else {
			ManagedLoggersRepository.logInfo(StaticComponentContainer.class, "Building static components by using configuration");
		}
		ManagedLoggersRepository.logInfo(StaticComponentContainer.class, "Instantiated {}", ManagedLoggersRepository.getClass().getName());
		try {			
			Strings = org.burningwave.core.Strings.create();
			Paths = org.burningwave.core.Strings.Paths.create();
			FileSystemHelper = org.burningwave.core.io.FileSystemHelper.create();
			Runtime.getRuntime().addShutdownHook(new Thread(FileSystemHelper::close));
			if (Boolean.valueOf(GlobalProperties.getProperty(CLEAR_TEMPORARY_FOLDER_ON_INIT_CONFIG_KEY))) {
				FileSystemHelper.clearMainTemporaryFolder();
			}
			ByteBufferDelegate = org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate.create();
			Streams = org.burningwave.core.io.Streams.create(GlobalProperties);
			JVMInfo = org.burningwave.core.jvm.JVMInfo.create();
			LowLevelObjectsHandler = org.burningwave.core.jvm.LowLevelObjectsHandler.create();
			Classes = org.burningwave.core.classes.Classes.create();
			ClassLoaders = org.burningwave.core.classes.Classes.Loaders.create();
			Cache = org.burningwave.core.Cache.create();
			Members = org.burningwave.core.classes.Members.create();
			Constructors = org.burningwave.core.reflection.Constructors.create();
			Fields = org.burningwave.core.reflection.Fields.create();
			Methods = org.burningwave.core.reflection.Methods.create();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(StaticComponentContainer.class, "Exception occurred", exc);
			throw Throwables.toRuntimeException(exc);
		}
	}

	static void showBanner() {
		List<String> bannerList = Arrays.asList(
			Resources.getAsStringBuffer(
				StaticComponentContainer.class.getClassLoader(), "org/burningwave/banner.bwb"
			).toString().split("-------------------------------------------------------------------------------------------------------------")	
		);
		Collections.shuffle(bannerList);
		System.out.println("\n" + bannerList.get(new Random().nextInt(bannerList.size())));
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
	
}
