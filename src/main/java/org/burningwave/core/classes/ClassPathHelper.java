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
package org.burningwave.core.classes;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.iterable.Properties;

public interface ClassPathHelper {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-path-helper.class-path-hunter.search-config.check-file-option";

		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			defaultValues.put(
				Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScanner.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static ClassPathHelper create(ClassPathHunter classPathHunter, Properties config) {
		return new ClassPathHelperImpl(classPathHunter, config);
	}
	
	public Supplier<Map<String, String>> computeByClassesSearching(Collection<String> classRepositories);

	public Supplier<Map<String, String>> computeByClassesSearching(Collection<String> classRepositories,
			ClassCriteria classCriteria);

	public Supplier<Map<String, String>> computeByClassesSearching(CacheableSearchConfig searchConfig);

	public Supplier<Map<String, String>> computeByClassesSearching(Collection<String> classRepositories,
			Collection<String> pathsToBeRefreshed, ClassCriteria classCriteria);

	public Supplier<Map<String, String>> computeFromSources(Collection<String> sources, Collection<String> classRepositories);

	public Supplier<Map<String, String>> computeFromSources(Collection<String> sources, Collection<String> classRepositories,
			ClassCriteria otherClassCriteria);
	
	@SuppressWarnings("unchecked")
	public Collection<String> searchWithoutTheUseOfCache(ClassCriteria classCriteria,  Collection<String>... pathColls);

	public Collection<String> searchWithoutTheUseOfCache(ClassCriteria classCriteria, String... path);

	public Supplier<Map<String, String>> computeFromSources(Collection<String> sources, Collection<String> classRepositories,
			Predicate<FileSystemItem> pathsToBeRefreshedPredicate,
			BiPredicate<FileSystemItem, JavaClass> javaClassFilterAdditionalFilter);

	public Supplier<Map<String, String>> compute(Collection<String> classRepositories,
			BiPredicate<FileSystemItem, JavaClass> javaClassProcessor);

	public Map<String, ClassLoader> computeAndAddAllToClassLoader(ClassLoader classLoader,
			Collection<String> classRepositories, String className, Collection<String> notFoundClasses);

	public Map<String, ClassLoader> computeAndAddAllToClassLoader(ClassLoader classLoader,
			Collection<String> classRepositories, Collection<String> pathsToBeRefreshed, String className,
			Collection<String> notFoundClasses);

	public Supplier<Map<String, String>> compute(Collection<String> classRepositories,
			Predicate<FileSystemItem> pathsToBeRefreshedPredicate,
			BiPredicate<FileSystemItem, JavaClass> javaClassFilter);
}
