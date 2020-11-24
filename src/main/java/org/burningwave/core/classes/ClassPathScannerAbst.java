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

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Component;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;


@SuppressWarnings("unchecked")
public abstract class ClassPathScannerAbst<I, C extends SearchContext<I>, R extends SearchResult<I>> implements Component {
	
	public static class Configuration {
		public static class Key {
			
			public final static String DEFAULT_CHECK_FILE_OPTIONS = "hunters.default-search-config.check-file-option";		
			public static final String DEFAULT_SEARCH_CONFIG_PATHS = PathHelper.Configuration.Key.PATHS_PREFIX + "hunters.default-search-config.paths";
						
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
	
		static {
			Map<String, Object> defaultValues = new HashMap<>();
	
			defaultValues.put(
				Key.DEFAULT_SEARCH_CONFIG_PATHS, 
				PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + PathHelper.Configuration.getPathsSeparator() +
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_PATHS_EXTENSION + "}" + PathHelper.Configuration.getPathsSeparator() + 
				"${" + PathHelper.Configuration.Key.MAIN_CLASS_REPOSITORIES + "}" + PathHelper.Configuration.getPathsSeparator()
			);
			defaultValues.put(
				Key.DEFAULT_CHECK_FILE_OPTIONS,
				"${" + PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	PathHelper pathHelper;
	Function<InitContext, C> contextSupplier;
	Function<C, R> resultSupplier;
	Properties config;
	Collection<SearchResult<I>> searchResults;
	String instanceId;
	ClassLoaderManager<PathScannerClassLoader> defaultPathScannerClassLoaderManager;
	
	ClassPathScannerAbst(
		PathHelper pathHelper,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Properties config
	) {
		this.pathHelper = pathHelper;
		this.contextSupplier = contextSupplier;
		this.resultSupplier = resultSupplier;
		this.config = config;
		this.searchResults = ConcurrentHashMap.newKeySet();
		instanceId = Objects.getCurrentId(this);
		this.defaultPathScannerClassLoaderManager = new ClassLoaderManager<>(
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier
		);
		listenTo(config);
	}
	
	@Override
	public <K, V> void processChangeNotification(
		Properties properties, Event event, K key, V newValue,
		V previousValue
	) {
		if (event.name().equals(Event.PUT.name())) {
			if (key instanceof String) {
				String keyAsString = (String)key;
				if (keyAsString.startsWith(getNameInConfigProperties() + ".default-path-scanner-class-loader")) {
					this.defaultPathScannerClassLoaderManager.reset();
				}
			}
		}
	}	
	
	abstract String getNameInConfigProperties();
	
	abstract String getDefaultPathScannerClassLoaderNameInConfigProperties();
	
	abstract String getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties();

	PathScannerClassLoader getDefaultPathScannerClassLoader(Object client) {
		return defaultPathScannerClassLoaderManager.get(client);
	}
	
	public R find() {
		return findBy(SearchConfig.withoutUsingCache());
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
		context.executeSearch((Consumer<SearchContext<I>>)searcher);
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		R searchResult = resultSupplier.apply(context);
		searchResult.setClassPathScanner(this);
		return searchResult;
	}
	
	void searchInFileSystem(C context) {
		SearchConfigAbst<?> searchConfig = context.getSearchConfig();
		FileSystemItem.Criteria filter = buildFileAndClassTesterAndExecutor(context, searchConfig.buildScanFileCriteria());
		IterableObjectHelper.iterateParallelIf(
			context.getSearchConfig().getPaths(), 
			basePath -> {
				FileSystemItem.ofPath(basePath).refresh().findInAllChildren(filter);
			},
			item -> item.size() > 1
		);		
	}
	
	FileSystemItem.Criteria buildFileAndClassTesterAndExecutor(C context, FileSystemItem.Criteria fileFilter) {
		Predicate<FileSystemItem[]> classFilePredicate = fileFilter.getOriginalPredicateOrTruePredicateIfPredicateIsNull();
		FileSystemItem.Criteria classTesterAndExecutor = FileSystemItem.Criteria.forAllFileThat(
			(child, basePath) -> {
				boolean isClass = false;
				if (isClass = classFilePredicate.test(new FileSystemItem[]{child, basePath})) {
					analyzeAndAddItemsToContext(context, child, basePath);
				}
				return isClass;
			}
		);
		
		if (fileFilter.hasNoExceptionHandler()) {
			classTesterAndExecutor.setDefaultExceptionHandler();
		} else {
			classTesterAndExecutor.setExceptionHandler(fileFilter.getExceptionHandler());
		}
		
		return classTesterAndExecutor;
	}

	void analyzeAndAddItemsToContext(C context, FileSystemItem child, FileSystemItem basePath) {
		JavaClass.use(child.toByteBuffer(), javaClass -> {
			ClassCriteria.TestContext criteriaTestContext = testClassCriteria(context, javaClass);
			if (criteriaTestContext.getResult()) {
				addToContext(
					context, criteriaTestContext, basePath.getAbsolutePath(), child, javaClass
				);
			}
		});
	}
	
	C createContext(SearchConfigAbst<?> searchConfig) {
		PathScannerClassLoader defaultPathScannerClassLoader = getDefaultPathScannerClassLoader(searchConfig);
		if (searchConfig.useDefaultPathScannerClassLoaderAsParent) {
			searchConfig.parentClassLoaderForPathScannerClassLoader = defaultPathScannerClassLoader;
		}
		C context = contextSupplier.apply(
			InitContext.create(
				defaultPathScannerClassLoader,
				searchConfig.useDefaultPathScannerClassLoader ?
					defaultPathScannerClassLoader :
					PathScannerClassLoader.create(
						searchConfig.parentClassLoaderForPathScannerClassLoader, 
						pathHelper,
						searchConfig.withDefaultScanFileCriteria(
							FileSystemItem.Criteria.forClassTypeFiles(
								config.resolveStringValue(
									getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties()
								)
							)
						).buildScanFileCriteria()
							
					),
				searchConfig
			)		
		);
		if (searchConfig.defaultScanFileCriteriaSupplier == null) {
			searchConfig.withDefaultScanFileCriteria(
				FileSystemItem.Criteria.forClassTypeFiles(
					config.resolveStringValue(Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS)
				)
			);
		}
		searchConfig.init(context.pathScannerClassLoader);
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
	
	boolean register(SearchResult<I> searchResult) {
		searchResults.add(searchResult);
		return true;
	}
	
	boolean unregister(SearchResult<I> searchResult) {
		searchResults.remove(searchResult);
		return true;
	}
	
	public synchronized void closeSearchResults() {
		Collection<SearchResult<I>> searchResults = this.searchResults;
		if (searchResults != null) {
			Iterator<SearchResult<I>> searchResultsIterator = searchResults.iterator();		
			while(searchResultsIterator.hasNext()) {
				SearchResult<I> searchResult = searchResultsIterator.next();
				searchResult.close();
			}
		}
	}
	
	@Override
	public void close() {
		unregister(config);
		pathHelper = null;
		contextSupplier = null;
		config = null;
		closeSearchResults();
		this.searchResults = null;
	}
}