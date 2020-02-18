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
package org.burningwave.core.classes.hunter;


import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.Classes;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.concurrent.ParallelTasksManager;
import org.burningwave.core.io.ClassFileScanConfig;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemScanner.Scan;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.IterableZipContainer;

public class ClassPathHunter extends ClassPathScannerWithCachingSupport<Collection<Class<?>>, ClassPathHunter.SearchContext, ClassPathHunter.SearchResult> {
	private ClassPathHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		Classes classes,
		ClassHelper classHelper,
		MemberFinder memberFinder
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			fileSystemScanner,
			pathHelper,
			streamHelper,
			classes,
			classHelper,
			memberFinder,
			(initContext) -> SearchContext._create(fileSystemHelper, streamHelper, initContext),
			(context) -> new ClassPathHunter.SearchResult(context)
		);
	}
	
	public static ClassPathHunter create(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		Classes classes,
		ClassHelper classHelper,
		MemberFinder memberFinder
	) {
		return new ClassPathHunter(byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, fileSystemScanner, pathHelper, streamHelper, classes, classHelper, memberFinder);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext context, String baseAbsolutePath, String currentScannedItemAbsolutePath, Collection<Class<?>> classes) {
		ClassCriteria.TestContext testContext = context.testCriteria(null);
		for (Class<?> cls : classes) {
			if ((testContext = context.testCriteria(context.retrieveClass(cls))).getResult()) {
				break;
			}
		}		
		return testContext;
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		SearchContext context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext,
		JavaClass javaClass
	) {	
		String classPath = scanItemContext.getScannedItem().getAbsolutePath();
		classPath = classPath.substring(
			0, classPath.lastIndexOf(
				javaClass.getPath(), classPath.length()
			)
		);	
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			classPath,
			context.loadClass(javaClass.getName())
		);
	}

	@Override
	void retrieveItemFromZipEntry(
		SearchContext context,
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext,
		JavaClass javaClass
	) {
		String fsObject = null;
		IterableZipContainer.Entry zipEntry = scanItemContext.getScannedItem().getWrappedItem();
		if (zipEntry.getName().equals(javaClass.getPath())) {
			fsObject = zipEntry.getParentContainer().getAbsolutePath();
		} else {
			String zipEntryAbsolutePath = zipEntry.getAbsolutePath();
			zipEntryAbsolutePath = zipEntryAbsolutePath.substring(0, zipEntryAbsolutePath.lastIndexOf(javaClass.getPath()));
			fsObject = zipEntryAbsolutePath;
		}
		context.addItemFound(scanItemContext.getBasePathAsString(), fsObject, context.loadClass(javaClass.getName()));
	}
	
	
	@Override
	public void close() {
		fileSystemHelper = null;
		super.close();
	}
	
	public static class SearchContext extends org.burningwave.core.classes.hunter.SearchContext<Collection<Class<?>>> {
		ParallelTasksManager tasksManager;
		
		SearchContext(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			super(fileSystemHelper, streamHelper, initContext);
			ClassFileScanConfig scanConfig = initContext.getClassFileScanConfiguration();
			this.tasksManager = ParallelTasksManager.create(scanConfig.getMaxParallelTasksForUnit());
		}		

		static SearchContext _create(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			return new SearchContext(fileSystemHelper, streamHelper,  initContext);
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
			tasksManager.close();
			super.close();
		}
	}
	
	public static class SearchResult extends org.burningwave.core.classes.hunter.SearchResult<Collection<Class<?>>> {
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
						).collect(Collectors.toCollection(() ->
							ConcurrentHashMap.newKeySet())
						);
					}
				}
			}
			return classPaths;
		}
	}
}
