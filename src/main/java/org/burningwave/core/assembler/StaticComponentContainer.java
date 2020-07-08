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

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.Properties;

public class StaticComponentContainer {
	public static class Configuration {
		public static class Key {
			private static final String CLEAR_TEMPORARY_FOLDER_ON_INIT = "static-component-container.clear-temporary-folder-on-init";
			private static final String HIDE_BANNER_ON_INIT = "static-component-container.hide-banner-on-init";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(Key.CLEAR_TEMPORARY_FOLDER_ON_INIT, "true");
			DEFAULT_VALUES.put(Key.HIDE_BANNER_ON_INIT, "false");
		}
	}
	public static final org.burningwave.core.classes.PropertyAccessor ByFieldOrByMethodPropertyAccessor;
	public static final org.burningwave.core.classes.PropertyAccessor ByMethodOrByFieldPropertyAccessor;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate ByteBufferDelegate;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
	public static final org.burningwave.core.classes.Constructors Constructors;
	public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
	public static final org.burningwave.core.classes.Fields Fields;
	public static final org.burningwave.core.iterable.Properties GlobalProperties;
	public static final org.burningwave.core.iterable.IterableObjectHelper IterableObjectHelper;
	public static final org.burningwave.core.jvm.JVMInfo JVMInfo;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
	public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
	public static final org.burningwave.core.classes.Members Members;
	public static final org.burningwave.core.classes.Methods Methods;
	public static final org.burningwave.core.Strings.Paths Paths;
	public static final org.burningwave.core.io.Resources Resources;
	public static final org.burningwave.core.io.Streams Streams;
	public static final org.burningwave.core.classes.SourceCodeHandler SourceCodeHandler;
	public static final org.burningwave.core.Strings Strings;
	public static final org.burningwave.core.Throwables Throwables;
	
	static {
		Properties properties = new Properties();
		properties.putAll(Configuration.DEFAULT_VALUES);
		properties.putAll(org.burningwave.core.io.Streams.Configuration.DEFAULT_VALUES);
		properties.putAll(org.burningwave.core.ManagedLogger.Repository.Configuration.DEFAULT_VALUES);
		
		Strings = org.burningwave.core.Strings.create();
		IterableObjectHelper = org.burningwave.core.iterable.IterableObjectHelper.create();
		Throwables = org.burningwave.core.Throwables.create();
		Resources = new org.burningwave.core.io.Resources();
		Map.Entry<org.burningwave.core.iterable.Properties, URL> propBag =
			Resources.loadFirstOneFound(properties, "burningwave.static.properties", "burningwave.static.default.properties");
		GlobalProperties = propBag.getKey();
		if (!Boolean.valueOf(GlobalProperties.getProperty(Configuration.Key.HIDE_BANNER_ON_INIT))) {
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
			Paths = org.burningwave.core.Strings.Paths.create();
			FileSystemHelper = org.burningwave.core.io.FileSystemHelper.create();
			Runtime.getRuntime().addShutdownHook(new Thread(FileSystemHelper::deleteTemporaryFolders));
			String clearTemporaryFolderFlag = GlobalProperties.getProperty(Configuration.Key.CLEAR_TEMPORARY_FOLDER_ON_INIT);
			if (Boolean.valueOf(clearTemporaryFolderFlag)) {
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
			Constructors = org.burningwave.core.classes.Constructors.create();
			Fields = org.burningwave.core.classes.Fields.create();
			Methods = org.burningwave.core.classes.Methods.create();
			ByFieldOrByMethodPropertyAccessor = org.burningwave.core.classes.PropertyAccessor.ByFieldOrByMethod.create();
			ByMethodOrByFieldPropertyAccessor = org.burningwave.core.classes.PropertyAccessor.ByMethodOrByField.create();
			SourceCodeHandler = org.burningwave.core.classes.SourceCodeHandler.create();
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
			String className = GlobalProperties.resolveStringValue(
				org.burningwave.core.ManagedLogger.Repository.Configuration.Key.TYPE,
				org.burningwave.core.ManagedLogger.Repository.Configuration.DEFAULT_VALUES
			);
			if ("autodetect".equalsIgnoreCase(className = className.trim())) {
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
