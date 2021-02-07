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

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.burningwave.core.assembler.ComponentSupplier;
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
			
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + "." + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_KEY + ".additional-imports}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR +
				PathScannerClassLoader.class.getName() + ";"
			);
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX, ClassHunter.class.getPackage().getName() + ".DefaultPathScannerClassLoaderRetrieverForClassHunter");
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
		return new ByteCodeHunterImpl(
			pathHelper,
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			config
		);
	}
	
	public static class SearchResult extends org.burningwave.core.classes.SearchResult<JavaClass> {
		
		SearchResult(SearchContext<JavaClass> context) {
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
