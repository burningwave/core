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

import java.util.Map;

import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class ByteCodeHunterImpl extends ClassPathScannerWithCachingSupport.Abst<JavaClass, SearchContext<JavaClass>, ByteCodeHunter.SearchResult> implements ByteCodeHunter {
	ByteCodeHunterImpl(
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