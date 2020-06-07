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
import java.util.function.BiPredicate;
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
	public R findBy(SearchConfig searchConfig) {
		return findBy(searchConfig, this::searchInFileSystem);
	}

	R findBy(SearchConfigAbst<?> input, Consumer<C> searcher) {
		SearchConfigAbst<?> searchConfig = input.createCopy();
		Collection<String> paths = searchConfig.getPaths();
		if (paths == null || paths.isEmpty()) {
			searchConfig.addPaths(pathHelper.getPaths(SearchConfigAbst.Key.DEFAULT_SEARCH_CONFIG_PATHS));
		}
		if (searchConfig.optimizePaths) {
			pathHelper.optimize(searchConfig.getPaths());
		}
		C context = createContext(
			searchConfig
		);
		searchConfig.init(context.pathScannerClassLoader);
		context.executeSearch(() -> {
			searcher.accept(context);
		});
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		return resultSupplier.apply(context);
	}

	void searchInFileSystem(C context) {
		final SearchConfigAbst<?> searchConfig = context.getSearchConfig();
		BiPredicate<FileSystemItem, FileSystemItem> filter = getTestItemPredicate(context);
		for (String path : searchConfig.getPaths()) {
			FileSystemItem.ofPath(path).refresh().getAllChildren(filter);
		}
	}

	BiPredicate<FileSystemItem, FileSystemItem> getTestItemPredicate(C context) {
		Predicate<FileSystemItem> classPredicate = context.getSearchConfig().parseCheckFileOptionsValue();
		return (basePath, child) -> {
			boolean isClass = false;
			try {
				if (isClass = classPredicate.test(child)) {
					JavaClass javaClass = JavaClass.create(child.toByteBuffer());
					ClassCriteria.TestContext criteriaTestContext = testCriteria(context, javaClass);
					if (criteriaTestContext.getResult()) {
						addToContext(
							context, criteriaTestContext, basePath.getAbsolutePath(), child, javaClass
						);
					}
				}
			} catch (Throwable exc) {
				logError("Could not scan " + child.getAbsolutePath(), exc);
			}
			return isClass;
		};
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
	
	abstract void addToContext(
		C context,
		ClassCriteria.TestContext criteriaTestContext,
		String basePath,
		FileSystemItem currentIteratedFile,
		JavaClass javaClass
	);
	
	@Override
	public void close() {
		byteCodeHunterSupplier = null;
		pathHelper = null;
		contextSupplier = null;
	}
}