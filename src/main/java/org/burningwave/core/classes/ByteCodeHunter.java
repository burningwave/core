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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public interface ByteCodeHunter extends ClassPathScannerWithCachingSupport<JavaClass, ByteCodeHunter.SearchResult> {
	
	public static class Configuration {
		
		public static class Key {
			
			public final static String NAME_IN_CONFIG_PROPERTIES = "byte-code-hunter";
			public final static String DEFAULT_PATH_SCANNER_CLASS_LOADER = NAME_IN_CONFIG_PROPERTIES + ".default-path-scanner-class-loader";
			public final static String PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = NAME_IN_CONFIG_PROPERTIES + ".new-isolated-path-scanner-class-loader.search-config.check-file-option";
			
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + ".additional-imports}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR +
				PathScannerClassLoader.class.getName() + ";"
			);
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX, ByteCodeHunter.class.getPackage().getName() + ".DefaultPathScannerClassLoaderRetrieverForByteCodeHunter");
			//DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			defaultValues.put(
				Key.DEFAULT_PATH_SCANNER_CLASS_LOADER, 
				(Function<ComponentSupplier, ClassLoader>)(componentSupplier) -> 
					componentSupplier.getPathScannerClassLoader()
			);
			defaultValues.put(
				Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScanner.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static ByteCodeHunter create(
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Properties config
	) {
		return new Impl(
			pathHelper,
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			config
		);
	}
	
	static class Impl extends ClassPathScannerWithCachingSupport.Abst<JavaClass, SearchContext<JavaClass>, ByteCodeHunter.SearchResult> implements ByteCodeHunter {
		private Impl(
			PathHelper pathHelper,
			Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			Properties config
		) {
			super(
				pathHelper,
				(initContext) -> SearchContext.<JavaClass>create(
					initContext
				),
				(context) -> new ByteCodeHunter.SearchResult(context),
				defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
				config
			);
		}
		
		@Override
		String getNameInConfigProperties() {
			return ByteCodeHunter.Configuration.Key.NAME_IN_CONFIG_PROPERTIES;
		}
		
		@Override
		String getDefaultPathScannerClassLoaderNameInConfigProperties() {
			return ByteCodeHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER;
		}
		
		@Override
		String getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties() {
			return ByteCodeHunter.Configuration.Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS;
		}
		
		@Override
		<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testClassCriteria(SearchContext<JavaClass> context, JavaClass javaClass) {
			return context.getSearchConfig().getClassCriteria().hasNoPredicate() ?
				context.getSearchConfig().getClassCriteria().testWithTrueResultForNullEntityOrTrueResultForNullPredicate(null) :
				super.testClassCriteria(context, javaClass);
		}
		
		@Override
		<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext<JavaClass> context, String path, String key, JavaClass javaClass) {
			return context.getSearchConfig().getClassCriteria().hasNoPredicate() ?
				context.getSearchConfig().getClassCriteria().testWithTrueResultForNullEntityOrTrueResultForNullPredicate(null) :				
				super.testClassCriteria(context, javaClass);
		}
		
		@Override
		void addToContext(SearchContext<JavaClass> context, TestContext criteriaTestContext,
			String basePath, FileSystemItem fileSystemItem, JavaClass javaClass
		) {
			context.addItemFound(basePath, fileSystemItem.getAbsolutePath(), javaClass.duplicate());		
		}
		
		@Override
		void clearItemsForPath(Map<String, JavaClass> items) {
			BackgroundExecutor.createTask(() -> {
				IterableObjectHelper.deepClear(items, (path, javaClass) -> javaClass.close());
			}, Thread.MIN_PRIORITY).submit();
		}
		
		@Override
		public void close() {
			closeResources(() -> this.cache == null, () -> {
				super.close();
			});
		}
	
	}
	
	public static class SearchResult extends org.burningwave.core.classes.SearchResult<JavaClass> {
		
		public SearchResult(SearchContext<JavaClass> context) {
			super(context);
		}
		
		public Collection<JavaClass> getClasses() {
			return context.getItemsFound();
		}
		
		public Map<String, JavaClass> getClassesFlatMap() {
			return context.getItemsFoundFlatMap();
		}
		
		public Map<String, ByteBuffer> getByteCodesFlatMap() {
			return getClassesFlatMap().entrySet().stream().collect(
				Collectors.toMap(e ->
					e.getValue().getName(), e -> e.getValue().getByteCode())
			);
		}
	}
}
