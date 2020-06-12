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
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class ClassPathHunter extends ClassPathScannerWithCachingSupport<Collection<Class<?>>, ClassPathHunter.SearchContext, ClassPathHunter.SearchResult> {
	private ClassPathHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Properties config
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			pathHelper,
			(initContext) -> SearchContext._create(initContext),
			(context) -> new ClassPathHunter.SearchResult(context),
			config
		);
	}
	
	public static ClassPathHunter create(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Properties config
	) {
		return new ClassPathHunter(
			byteCodeHunterSupplier,
			classHunterSupplier,
			pathHelper,
			config
		);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(
		SearchContext context, String baseAbsolutePath, String currentScannedItemAbsolutePath, Collection<Class<?>> classes
	) {
		ClassCriteria.TestContext testContext = context.testClassCriteria(null);
		for (Class<?> cls : classes) {
			if ((testContext = context.testClassCriteria(context.retrieveClass(cls))).getResult()) {
				break;
			}
		}		
		return testContext;
	}
	
	@Override
	TestContext testPathAndCachedItem(
		SearchContext context,
		FileSystemItem[] filesToBeTested, 
		Collection<Class<?>> classes, 
		Predicate<FileSystemItem[]> fileFilterPredicate
	) {
		AtomicReference<ClassCriteria.TestContext> criteriaTestContextAR = new AtomicReference<>(context.testClassCriteria(null));
		filesToBeTested[0].findFirstOfAllChildren(
			FileSystemItem.Criteria.forAllFileThat(
				(basePath, child) -> {
					boolean matchPredicate = false;
					if (matchPredicate = child.isChildOf(filesToBeTested[1]) && fileFilterPredicate.test(new FileSystemItem[]{child, basePath})) {
						criteriaTestContextAR.set(
							testCachedItem(
								context, filesToBeTested[1].getAbsolutePath(), filesToBeTested[0].getAbsolutePath(), classes
							)
						);
					}
					return matchPredicate;
				}
			)
		);
		return criteriaTestContextAR.get();
	}
	
	@Override
	void addToContext(SearchContext context, TestContext criteriaTestContext,
		String basePath, FileSystemItem fileSystemItem, JavaClass javaClass
	) {
		String classPath = fileSystemItem.getAbsolutePath();
		classPath = classPath.substring(0, classPath.lastIndexOf(javaClass.getName().replace(".", "/")));
		context.addItemFound(basePath, classPath, context.loadClass(javaClass.getName()));		
	}
	
	@Override
	public void close() {
		super.close();
	}
	
	public static class SearchContext extends org.burningwave.core.classes.SearchContext<Collection<Class<?>>> {
		
		SearchContext(InitContext initContext) {
			super(initContext);
		}		

		static SearchContext _create(InitContext initContext) {
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
		
		@Override
		public void close() {
			super.close();
		}
	}
	
	public static class SearchResult extends org.burningwave.core.classes.SearchResult<Collection<Class<?>>> {
		Collection<FileSystemItem> classPaths;
		
		public SearchResult(SearchContext context) {
			super(context);
		}
		
		public Collection<FileSystemItem> getClassPaths() {
			if (classPaths == null) {
				Map<String, Collection<Class<?>>> itemsFoundFlatMaps = context.getItemsFoundFlatMap();
				synchronized (itemsFoundFlatMaps) {
					if (classPaths == null) {
						classPaths = itemsFoundFlatMaps.keySet().stream().map(path -> 
							FileSystemItem.ofPath(path)
						).collect(
							Collectors.toCollection(
								HashSet::new
							)
						);
					}
				}
			}
			return classPaths;
		}
	}

}
