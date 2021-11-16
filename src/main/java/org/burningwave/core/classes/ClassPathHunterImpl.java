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
 * Copyright (c) 2019-2021 Roberto Gentili
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;

class ClassPathHunterImpl extends ClassPathScanner.Abst<Collection<Class<?>>, ClassPathHunterImpl.SearchContext, ClassPathHunter.SearchResult> implements ClassPathHunter {

	ClassPathHunterImpl(
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Map<?, ?> config
	) {
		super(
			pathHelper,
			(initContext) -> SearchContext._create(initContext),
			(context) -> new ClassPathHunter.SearchResult(context),
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			config
		);
	}


	@Override
	String getNameInConfigProperties() {
		return ClassPathHunter.Configuration.Key.NAME_IN_CONFIG_PROPERTIES;
	}

	@Override
	String getDefaultPathScannerClassLoaderNameInConfigProperties() {
		return ClassPathHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER;
	}

	@Override
	String getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties() {
		return ClassPathHunter.Configuration.Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS;
	}


	@Override
	void addToContext(ClassPathHunterImpl.SearchContext context, TestContext criteriaTestContext,
		String basePath, FileSystemItem fileSystemItem, JavaClass javaClass
	) {
		String classPath = fileSystemItem.getAbsolutePath();
		FileSystemItem classPathAsFIS = FileSystemItem.ofPath(classPath.substring(0, classPath.lastIndexOf(javaClass.getPath())));
		context.addItemFound(basePath, classPathAsFIS.getAbsolutePath(), context.loadClass(javaClass.getName()));
	}

	@Override
	public void close() {
		closeResources(() -> this.pathHelper == null, task -> {
			super.close();
		});
	}

	static class SearchContext extends org.burningwave.core.classes.SearchContext<Collection<Class<?>>> {

		SearchContext(InitContext initContext) {
			super(initContext);
		}

		static ClassPathHunterImpl.SearchContext _create(InitContext initContext) {
			return new SearchContext(initContext);
		}


		void addItemFound(String basePathAsString, String classPathAsFile, Class<?> testedClass) {
			Map<String, Collection<Class<?>>> testedClassesForClassPathMap = retrieveCollectionForPath(
				itemsFoundMap,
				ConcurrentHashMap::new,
				basePathAsString
			);
			Collection<Class<?>> testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
			if (testedClassesForClassPath == null) {
				synchronized (testedClassesForClassPathMap) {
					testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
					if (testedClassesForClassPath == null) {
						testedClassesForClassPathMap.put(classPathAsFile, testedClassesForClassPath = ConcurrentHashMap.newKeySet());
					}
				}
			}
			testedClassesForClassPath.add(testedClass);
			itemsFoundFlatMap.putAll(testedClassesForClassPathMap);
		}
	}

}