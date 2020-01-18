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

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Context;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.StreamHelper;

public class SearchContext<T> implements Component {

	FileSystemHelper fileSystemHelper;
	ClassFileScanConfig classFileScanConfiguration;
	SearchConfigAbst<?> searchConfig;
	Map<String, T> itemsFoundFlatMap;
	Map<String, Map<String, T>> itemsFoundMap;
	PathMemoryClassLoader sharedPathMemoryClassLoader;
	PathMemoryClassLoader pathMemoryClassLoader;
	Collection<String> skippedClassNames;
	Boolean classLoaderHaveBeenUploadedWithCriteriaPaths;
	CompletableFuture<Void> searchTask;
	Collection<T> itemsFound;
	boolean searchTaskFinished;
	
	Collection<String> getSkippedClassNames() {
		return skippedClassNames;
	}
	

	SearchContext(
		FileSystemHelper fileSystemHelper,
		StreamHelper streamHelper,
		InitContext initContext
	) {
		this.fileSystemHelper = fileSystemHelper;
		this.itemsFoundFlatMap = new ConcurrentHashMap<>();
		this.itemsFoundMap = new ConcurrentHashMap<>();
		this.skippedClassNames = ConcurrentHashMap.newKeySet();
		this.sharedPathMemoryClassLoader = initContext.getSharedPathMemoryClassLoader();
		this.pathMemoryClassLoader = initContext.getPathMemoryClassLoader();
		this.classFileScanConfiguration = initContext.getClassFileScanConfiguration();
		this.searchConfig = initContext.getSearchCriteria();
		this.classLoaderHaveBeenUploadedWithCriteriaPaths = pathMemoryClassLoader.checkPaths(
			classFileScanConfiguration.getPaths(), searchConfig.considerURLClassLoaderPathsAsScanned
		).getNotContainedPaths().isEmpty();
	}
	
	public static <T> SearchContext<T> create(
		FileSystemHelper fileSystemHelper,
		StreamHelper streamHelper,
		InitContext initContext
	) {
		return new SearchContext<>(fileSystemHelper, streamHelper, initContext);
	}
	
	void executeSearch(Runnable searcher) {
		if (searchConfig.waitForSearchEnding) {
			searcher.run();
			searchTaskFinished = true;
		} else {
			searchTask = CompletableFuture.runAsync(() -> {
				searcher.run();
				searchTaskFinished = true;
			});
		}
	}
	
	void waitForSearchEnding() {
		try {
			searchTask.get();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	void addItemFound(String path, String key, T item) {
		retrieveCollectionForPath(
			itemsFoundMap,
			ConcurrentHashMap::new, path
		).put(key, item);
		itemsFoundFlatMap.put(key, item);
	}
	
	void addAllItemsFound(String path, Map<String, T> items) {
		retrieveCollectionForPath(
			itemsFoundMap,
			ConcurrentHashMap::new, path
		).putAll(items);
		itemsFoundFlatMap.putAll(items);
	}
	
	 Map<String, T> retrieveCollectionForPath(Map<String, Map<String, T>> allItems, Supplier<Map<String, T>> mapForPathSupplier, String path) {
		Map<String, T> items = null;
		if (mapForPathSupplier != null) {
			if (allItems != null) {
				items = allItems.get(path);
				if (items == null) {
					synchronized(allItems) {
						items = allItems.get(path);
						if (items == null) {
							items = mapForPathSupplier.get();
							allItems.put(path, items);
						}
					}
				}
			} else {
				items = mapForPathSupplier.get();
			}
		}
		return items;
	}
	
	Collection<String> getPathsToBeScanned() {
		return classFileScanConfiguration.getPaths();
	}
	
	@SuppressWarnings("unchecked")
	<C extends SearchConfigAbst<C>> C getSearchConfig() {
		return (C)searchConfig;
	}
	
	Map<String, T> getItemsFoundFlatMap() {
		return this.itemsFoundFlatMap;
	}
	
	Collection<T> getItemsFound() {
		if (itemsFound == null) {
			synchronized(itemsFoundFlatMap) {
				if (itemsFound == null) {
					this.itemsFound = ConcurrentHashMap.newKeySet();
					this.itemsFound.addAll(this.itemsFoundFlatMap.values());
				}
			}
		}
		return itemsFound;
	}
	
	Map<String, T> getItemsFound(String path) {
		return this.itemsFoundMap.get(path);
	}
			
	
	public void addByteCodeClassesToClassLoader(String className, ByteBuffer byteCode) {
		pathMemoryClassLoader.addCompiledClass(className, byteCode);			
	}
	
	protected <O> O execute(ThrowingSupplier<O> supplier, Supplier<O> defaultValueSupplier, Supplier<String> classNameSupplier) {
		return ThrowingSupplier.get(() -> {
			try {
				return supplier.get();
			} catch (ClassNotFoundException | NoClassDefFoundError exc) {
				String notFoundClassName = exc.getMessage().replace("/", ".");
				if (!skippedClassNames.contains(notFoundClassName)) {
					if (!this.classLoaderHaveBeenUploadedWithCriteriaPaths) {
						synchronized(this.classLoaderHaveBeenUploadedWithCriteriaPaths) {
							if (!this.classLoaderHaveBeenUploadedWithCriteriaPaths) {
								pathMemoryClassLoader.scanPathsAndLoadAllFoundClasses(
									getPathsToBeScanned(), searchConfig.considerURLClassLoaderPathsAsScanned, classFileScanConfiguration.maxParallelTasksForUnit
								);
								this.classLoaderHaveBeenUploadedWithCriteriaPaths = true;
							}
						}
						return execute(supplier, defaultValueSupplier, classNameSupplier);
					} else {
						skippedClassNames.add(classNameSupplier.get());
						skippedClassNames.add(notFoundClassName);
					}
				}
				return defaultValueSupplier.get();
			} catch (ClassFormatError | ClassCircularityError | IncompatibleClassChangeError | VerifyError exc) {
				logWarn("Could not load class {}: {}", classNameSupplier.get(), exc.toString());
			}
			return defaultValueSupplier.get();
		});
	}
	
	protected Class<?> loadClass(String className) {
		return execute(
			() -> pathMemoryClassLoader.loadClass(className), 
			() -> null, 
			() -> className
		);
	}
	
	protected Class<?> retrieveClass(Class<?> cls) {
		return ClassHelper.isLoadedBy(cls, pathMemoryClassLoader) ?
			cls : 
			loadClass(cls.getName());
	}
	
	<C extends SearchConfigAbst<C>> ClassCriteria.TestContext testCriteria(Class<?> cls) {
		return (ClassCriteria.TestContext) execute(
			() -> searchConfig.getClassCriteria().testAndReturnFalseIfNullOrTrueByDefault(cls), 
			() -> searchConfig.getClassCriteria().testAndReturnFalseIfNullOrFalseByDefault(null), 
			() -> cls.getName()
		);
	}
	
	@Override
	public void close() {
		fileSystemHelper = null;
		if (searchConfig.deleteFoundItemsOnClose) {
			itemsFoundFlatMap.clear();
			itemsFoundMap.entrySet().stream().forEach(entry -> {
				entry.getValue().clear();
			});
		}
		itemsFoundFlatMap = null;
		itemsFoundMap = null;
		searchConfig = null;
		if (pathMemoryClassLoader != sharedPathMemoryClassLoader) {
			pathMemoryClassLoader.close();
		}
		pathMemoryClassLoader = null;
		sharedPathMemoryClassLoader = null;
		skippedClassNames.clear();
		skippedClassNames = null;
	}
	
	
	static class InitContext extends Context {
		enum Elements {
			SHARED_PATH_MEMORY_CLASS_LOADER,
			PATH_MEMORY_CLASS_LOADER,
			CLASS_FILE_SCAN_CONFIGURATION,
			SEARCH_CRITERIA;
		}
		
		InitContext(
			PathMemoryClassLoader sharedPathMemoryClassLoader, 
			PathMemoryClassLoader pathMemoryClassLoader,
			ClassFileScanConfig classFileScanConfiguration,
			SearchConfigAbst<?> criteria
		) {
			super();
			put(Elements.SHARED_PATH_MEMORY_CLASS_LOADER, sharedPathMemoryClassLoader);
			put(Elements.PATH_MEMORY_CLASS_LOADER, pathMemoryClassLoader);
			put(Elements.CLASS_FILE_SCAN_CONFIGURATION, classFileScanConfiguration);
			put(Elements.SEARCH_CRITERIA, criteria);			
		}
		
		static InitContext create(
			PathMemoryClassLoader sharedPathMemoryClassLoader, 
			PathMemoryClassLoader pathMemoryClassLoader,
			ClassFileScanConfig classFileScanConfiguration,
			SearchConfigAbst<?> criteria
		) {
			return new InitContext(sharedPathMemoryClassLoader, pathMemoryClassLoader, classFileScanConfiguration, criteria);
		}
		
		PathMemoryClassLoader getSharedPathMemoryClassLoader() {
			return get(Elements.SHARED_PATH_MEMORY_CLASS_LOADER);
		}
		
		PathMemoryClassLoader getPathMemoryClassLoader() {
			return get(Elements.PATH_MEMORY_CLASS_LOADER);
		}
		
		ClassFileScanConfig getClassFileScanConfiguration() {
			return get(Elements.CLASS_FILE_SCAN_CONFIGURATION);
		}
		
		<C extends SearchConfigAbst<C>> C getSearchCriteria() {
			return get(Elements.SEARCH_CRITERIA);
		}
	}
}