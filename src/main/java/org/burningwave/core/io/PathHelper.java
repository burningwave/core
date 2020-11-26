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
package org.burningwave.core.io;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.burningwave.core.iterable.Properties;


public interface PathHelper {
	
	public static class Configuration {
		
		public static class Key {
			public static String PATHS_PREFIX = "paths.";
			public static String MAIN_CLASS_PATHS = PATHS_PREFIX + "main-class-paths";
			public static String MAIN_CLASS_PATHS_PLACE_HOLDER = "${" + MAIN_CLASS_PATHS + "}";
			public static String MAIN_CLASS_PATHS_EXTENSION = MAIN_CLASS_PATHS + ".extension";
			public static String MAIN_CLASS_REPOSITORIES = PATHS_PREFIX + "main-class-repositories";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			defaultValues.put(Key.MAIN_CLASS_PATHS, "${system.properties:java.class.path}");
			defaultValues.put(
				Key.MAIN_CLASS_PATHS_EXTENSION, 
				"//${system.properties:java.home}/lib//children:.*?\\.jar" + PathHelper.Configuration.getPathsSeparator() +
				"//${system.properties:java.home}/lib/ext//children:.*?\\.jar" + PathHelper.Configuration.getPathsSeparator()
			);
			defaultValues.put(
				Key.MAIN_CLASS_REPOSITORIES, 
				"//${system.properties:java.home}/jmods//children:.*?\\.jmod" + PathHelper.Configuration.getPathsSeparator()
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
		
		public static String getPathsSeparator() {
			return IterableObjectHelper.getDefaultValuesSeparator();
		}
	}	
	
	public static PathHelper create(Properties config) {
		return new PathHelperImpl(config);
	}
	
	String getBurningwaveRuntimeClassPath();

	public Collection<String> getMainClassPaths();

	public Collection<String> getAllMainClassPaths();

	public Collection<String> getAllPaths();

	public Collection<String> getPaths(String... names);

	public Collection<String> loadAndMapPaths(String pathGroupName, String paths);

	public Collection<FileSystemItem> findResources(Predicate<String> absolutePathPredicate);

	public Collection<String> getAbsolutePathsOfResources(Predicate<String> absolutePathPredicate);

	public String getAbsolutePathOfResource(String resourceRelativePath);

	public Collection<String> getAbsolutePathsOfResource(String... resourceRelativePath);

	public Collection<FileSystemItem> getResources(String... resourcesRelativePaths);

	public FileSystemItem getResource(String resourceRelativePath);

	public <T> Collection<T> getResources(BiConsumer<Collection<T>, FileSystemItem> fileConsumer, String... resourcesRelativePaths);

	public <T> T getResource(BiConsumer<Collection<T>, FileSystemItem> fileConsumer, String resourceRelativePath);

	public Collection<InputStream> getResourcesAsStreams(String... resourcesRelativePaths);

	public InputStream getResourceAsStream(String resourceRelativePath);

	public StringBuffer getResourceAsStringBuffer(String resourceRelativePath);

	public Collection<String> optimize(String... paths);

	public Collection<String> optimize(Collection<String> paths);

	public Collection<String> getPaths(Predicate<String> pathPredicate);

	public String getPath(Predicate<String> pathPredicate);
}
