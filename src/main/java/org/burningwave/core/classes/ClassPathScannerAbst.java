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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;


public abstract class ClassPathScannerAbst<I, C extends SearchContext<I>, R extends SearchResult<I>> implements Component {
	
	Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	ByteCodeHunter byteCodeHunter;
	Supplier<ClassHunter> classHunterSupplier;
	ClassHunter classHunter;
	PathHelper pathHelper;
	Function<InitContext, C> contextSupplier;
	Function<C, R> resultSupplier;

	ClassPathScannerAbst(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier
	) {
		this.pathHelper = pathHelper;
		this.byteCodeHunterSupplier = byteCodeHunterSupplier;
		this.classHunterSupplier = classHunterSupplier;
		this.contextSupplier = contextSupplier;
		this.resultSupplier = resultSupplier;
	}
	
	ClassHunter getClassHunter() {
		return classHunter != null ?
			classHunter	:
			(classHunter = classHunterSupplier.get());
	}
	
	
	ByteCodeHunter getByteCodeHunter() {
		return byteCodeHunter != null ?
			byteCodeHunter :
			(byteCodeHunter = byteCodeHunterSupplier.get());	
	}
	
	//Not cached search
	public R findBy(SearchConfig input) {
		SearchConfig searchConfig = input.createCopy();
		if (searchConfig.getPaths().isEmpty()) {
			searchConfig.addPaths(pathHelper.getPaths(SearchConfigAbst.Key.DEFAULT_SEARCH_CONFIG_PATHS));
		}
		C context = createContext(searchConfig);
		searchConfig.init(context.pathScannerClassLoader);
		context.executeSearch(() -> {
			search(context, null, null);
		});
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		return resultSupplier.apply(context);
	}

	void search(C context, Collection<String> paths, Consumer<FileSystemItem> afterScanPath) {
		final SearchConfigAbst<?> searchConfig = context.getSearchConfig();
		paths = paths != null ? paths : searchConfig.getPaths();
		if (searchConfig.optimizePaths) {
			pathHelper.optimize(paths);
		}
		Predicate<FileSystemItem> classPredicate = searchConfig.parseCheckFileOptionsValue();
		for (String path : paths != null ? paths : searchConfig.getPaths()) {
			FileSystemItem fileSystemItem = FileSystemItem.ofPath(path);
			if (searchConfig instanceof SearchConfig) {
				fileSystemItem.refresh();
			}
			fileSystemItem.getAllChildren(fIS -> {
				try {
					if (classPredicate.test(fIS)) {
						JavaClass javaClass = JavaClass.create(fIS.toByteBuffer());
						ClassCriteria.TestContext criteriaTestContext = testCriteria(context, javaClass);
						if (criteriaTestContext.getResult()) {
							retrieveItem(
								path, context, criteriaTestContext, fIS, javaClass
							);
						}
						return true;
					}
					return false;
				} catch (Throwable exc) {
					logError("Could not scan " + fIS.getAbsolutePath(), exc);
					return false;
				}
			});
			if (afterScanPath != null) {
				afterScanPath.accept(fileSystemItem);
			}
		}
	}
	
	@SuppressWarnings("resource")
	C createContext(SearchConfigAbst<?> searchConfig) {
		PathScannerClassLoader sharedClassLoader = getClassHunter().pathScannerClassLoader;
		if (searchConfig.useSharedClassLoaderAsParent) {
			searchConfig.parentClassLoaderForMainClassLoader = sharedClassLoader;
		}
		C context = contextSupplier.apply(
			InitContext.create(
				sharedClassLoader,
				searchConfig.useSharedClassLoaderAsMain ?
					sharedClassLoader :
					PathScannerClassLoader.create(
						searchConfig.parentClassLoaderForMainClassLoader, 
						pathHelper, byteCodeHunterSupplier, searchConfig.getCheckFileOptions()
					),
				searchConfig
			)		
		);
		return context;
	}
	
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCriteria(C context, JavaClass javaClass) {
		return context.testCriteria(context.loadClass(javaClass.getName()));
	}
	
	abstract void retrieveItem(String basePath, C context, ClassCriteria.TestContext criteriaTestContext, FileSystemItem fileSystemItem, JavaClass javaClass);
	
	@Override
	public void close() {
		byteCodeHunterSupplier = null;
		pathHelper = null;
		contextSupplier = null;
	}
}