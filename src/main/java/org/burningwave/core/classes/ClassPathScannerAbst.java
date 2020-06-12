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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;


@SuppressWarnings("unchecked")
public abstract class ClassPathScannerAbst<I, C extends SearchContext<I>, R extends SearchResult<I>> implements Component {
	
	public static class Configuration {
		public static class Key {
			
			public final static String DEFAULT_CHECK_FILE_OPTIONS = "hunters.default-search-config.check-file-option";		
			public static final String DEFAULT_SEARCH_CONFIG_PATHS = PathHelper.Configuration.Key.PATHS_PREFIX + "hunters.default-search-config.paths";
						
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
	
		static {
			DEFAULT_VALUES = new HashMap<>();
	
			DEFAULT_VALUES.put(
				Key.DEFAULT_SEARCH_CONFIG_PATHS, 
				PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + ";"
			);
			DEFAULT_VALUES.put(
				Key.DEFAULT_CHECK_FILE_OPTIONS,
				FileSystemItem.CheckingOption.FOR_NAME.getLabel()
			);
		}
	}
	
	Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	ByteCodeHunter byteCodeHunter;
	Supplier<ClassHunter> classHunterSupplier;
	ClassHunter classHunter;
	PathHelper pathHelper;
	Function<InitContext, C> contextSupplier;
	Function<C, R> resultSupplier;
	Properties config;

	ClassPathScannerAbst(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier,
		Properties config
	) {
		this.pathHelper = pathHelper;
		this.byteCodeHunterSupplier = byteCodeHunterSupplier;
		this.classHunterSupplier = classHunterSupplier;
		this.contextSupplier = contextSupplier;
		this.resultSupplier = resultSupplier;
		this.config = config;
		listenTo(config);
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
			searchConfig.addPaths(pathHelper.getPaths(Configuration.Key.DEFAULT_SEARCH_CONFIG_PATHS));
		}
		if (searchConfig.optimizePaths) {
			pathHelper.optimize(searchConfig.getPaths());
		}
		C context = createContext(
			searchConfig
		);
		searchConfig.init(context.pathScannerClassLoader);
		context.executeSearch((Consumer<SearchContext<I>>)searcher);
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		return resultSupplier.apply(context);
	}
	
	void searchInFileSystem(C context) {
		FileSystemItem.Criteria filter = getFileAndClassTesterAndExecutor(context);
		for (String path : context.getSearchConfig().getPaths()) {
			FileSystemItem.ofPath(path).refresh().findInAllChildren(filter);
		}
	}
	
	FileSystemItem.Criteria getFileAndClassTesterAndExecutor(C context) {
		SearchConfigAbst<?> searchConfig = context.getSearchConfig();
		if (searchConfig.getScanFileCriteria().hasNoPredicate()) {
			searchConfig.withScanFileCriteria(
				FileSystemItem.Criteria.forClassTypeFiles(
					(String)config.resolve(Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS, Configuration.DEFAULT_VALUES)
				)
			);
		}

		Predicate<FileSystemItem[]> classFilePredicate = searchConfig.getScanFileCriteria().getPredicateOrTruePredicateIfPredicateIsNull();
		return getFileAndClassTesterAndExecutor(context, classFilePredicate);
	}

	FileSystemItem.Criteria getFileAndClassTesterAndExecutor(C context, Predicate<FileSystemItem[]> classFilePredicate) {
		return FileSystemItem.Criteria.forAllFileThat(
			(child, basePath) -> {
				boolean isClass = false;
				try {
					if (isClass = classFilePredicate.test(new FileSystemItem[]{child, basePath})) {
						JavaClass javaClass = JavaClass.create(child.toByteBuffer());
						ClassCriteria.TestContext criteriaTestContext = testClassCriteria(context, javaClass);
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
			}
		);
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
						pathHelper, byteCodeHunterSupplier, searchConfig.getScanFileCriteria()
					),
				searchConfig
			)		
		);
		return context;
	}
	
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testClassCriteria(C context, JavaClass javaClass) {
		return context.test(context.loadClass(javaClass.getName()));
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
		unregister(config);
		byteCodeHunterSupplier = null;
		pathHelper = null;
		contextSupplier = null;
		config = null;
	}
}