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
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.Closeable;
import org.burningwave.core.Context;
import org.burningwave.core.concurrent.QueuedTaskExecutor;
import org.burningwave.core.function.Executor;
import org.burningwave.core.function.ThrowingSupplier;

class SearchContext<T> implements Closeable {

	SearchConfig searchConfig;
	Map<String, T> itemsFoundFlatMap;
	Map<String, Map<String, T>> itemsFoundMap;
	PathScannerClassLoader sharedPathScannerClassLoader;
	PathScannerClassLoader pathScannerClassLoader;
	Collection<String> skippedClassNames;
	QueuedTaskExecutor.Task searchTask;
	Collection<T> itemsFound;
	boolean requestToClosePathScannderClassLoaderOnClose;

	Collection<String> getSkippedClassNames() {
		return skippedClassNames;
	}


	SearchContext(
		InitContext initContext
	) {
		this.itemsFoundFlatMap = new ConcurrentHashMap<>();
		this.itemsFoundMap = new ConcurrentHashMap<>();
		this.skippedClassNames = ConcurrentHashMap.newKeySet();
		this.sharedPathScannerClassLoader = initContext.getSharedPathScannerClassLoader();
		this.pathScannerClassLoader = initContext.getPathScannerClassLoader();
		this.searchConfig = initContext.getSearchConfig();
		this.pathScannerClassLoader.register(this);
		this.sharedPathScannerClassLoader.register(this);
		this.requestToClosePathScannderClassLoaderOnClose = true;
	}

	public static <T> SearchContext<T> create(
		InitContext initContext
	) {
		return new SearchContext<>(initContext);
	}

	void executeSearch(Runnable searcher) {
		Integer priority = searchConfig.priority;
		Thread currentThread = Thread.currentThread();
		int initialThreadPriority = currentThread.getPriority();
		if (priority == null) {
			priority = initialThreadPriority;
		}
		if (searchConfig.waitForSearchEnding) {
			try {
				if (initialThreadPriority != priority) {
					currentThread.setPriority(priority);
				}
				searcher.run();
			} finally {
				if (initialThreadPriority != priority) {
					currentThread.setPriority(initialThreadPriority);
				}
			}
		} else {
			searchTask = BackgroundExecutor.createTask(task -> {
					searcher.run();
				},
				priority
			).submit();
		}
	}

	void waitForSearchEnding() {
		QueuedTaskExecutor.Task searchTask = this.searchTask;
		if (searchTask != null) {
			searchTask.join();
		}
	}

	QueuedTaskExecutor.Task getSearchTask() {
		return this.searchTask;
	}

	void addItemFound(String path, String key, T item) {
		retrieveCollectionForPath(
			itemsFoundMap,
			ConcurrentHashMap::new, path
		).put(key, item);
		synchronized(itemsFoundFlatMap) {
			itemsFoundFlatMap.put(key, item);
		}
	}

	void addAllItemsFound(String path, Map<String, T> items) {
		retrieveCollectionForPath(
			itemsFoundMap,
			ConcurrentHashMap::new, path
		).putAll(items);
		for (Map.Entry<String, T> item : items.entrySet()) {
			synchronized(itemsFoundFlatMap) {
				itemsFoundFlatMap.put(item.getKey(), item.getValue());
			}
		}
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


	SearchConfig getSearchConfig() {
		return searchConfig;
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

	void setrequestToClosePathScannderClassLoaderOnClose(boolean flag) {
		this.requestToClosePathScannderClassLoaderOnClose = flag;
	}

	Map<String, T> getItemsFound(String path) {
		return this.itemsFoundMap.get(path);
	}


	void addByteCodeClassesToClassLoader(String className, ByteBuffer byteCode) {
		pathScannerClassLoader.addByteCode(className, byteCode);
	}

	<O> O execute(ThrowingSupplier<O, Throwable> supplier, Supplier<O> defaultValueSupplier, Supplier<String> classNameSupplier) {
		return Executor.get(() -> {
			try {
				return supplier.get();
			} catch (ClassNotFoundException | NoClassDefFoundError exc) {
				String notFoundClassName = Classes.retrieveName(exc);
				addToSkippedClassNames(classNameSupplier.get());
				addToSkippedClassNames(notFoundClassName);
				ManagedLoggerRepository.logWarn(getClass()::getName, "Could not load class {}: {}", classNameSupplier.get(), exc.toString());
			} catch (LinkageError | SecurityException | InternalError exc) {
				ManagedLoggerRepository.logWarn(getClass()::getName, "Could not load class {}: {}", classNameSupplier.get(), exc.toString());
			}
			return defaultValueSupplier.get();
		});
	}

	void addToSkippedClassNames(String className) {
		if (className != null) {
			skippedClassNames.add(className);
		}
	}

	Class<?> loadClass(String className) {
		return execute(
			() -> pathScannerClassLoader.loadClass(className),
			() -> null,
			() -> className
		);
	}

	Class<?> loadClass(Class<?> cls) {
		return execute(
			() -> pathScannerClassLoader.loadOrDefineClass(cls),
			() -> null,
			() -> cls.getName()
		);
	}

	Class<?> loadClass(JavaClass cls) {
		return execute(
			() -> pathScannerClassLoader.loadOrDefineClass(cls),
			() -> null,
			() -> cls.getName()
		);
	}

	Class<?> retrieveClass(Class<?> cls) {
		return Classes.isLoadedBy(cls, pathScannerClassLoader) ?
			cls :
			loadClass(cls.getName());
	}

	ClassCriteria.TestContext test(Class<?> cls) {
		return execute(
			() -> searchConfig.getClassCriteria().testWithFalseResultForNullEntityOrTrueResultForNullPredicate(cls),
			() -> searchConfig.getClassCriteria().testWithFalseResultForNullEntityOrFalseResultForNullPredicate(null),
			() -> cls.getName()
		);
	}

	@Override
	public void close() {
		pathScannerClassLoader.unregister(this, true);
		if (sharedPathScannerClassLoader != null) {
			sharedPathScannerClassLoader.unregister(this, requestToClosePathScannderClassLoaderOnClose);
		}
		itemsFoundFlatMap = null;
		itemsFoundMap = null;
		itemsFound = null;
		searchConfig.close();
		searchConfig = null;
		pathScannerClassLoader = null;
		sharedPathScannerClassLoader = null;
		skippedClassNames.clear();
		skippedClassNames = null;
		searchTask = null;
	}


	static class InitContext extends Context {
		enum Elements {
			SHARED_PATH_SCANNER_CLASS_LOADER,
			PATH_SCANNER_CLASS_LOADER,
			SEARCH_CONFIG;
		}

		InitContext(
			PathScannerClassLoader sharedPathMemoryClassLoader,
			PathScannerClassLoader pathScannerClassLoader,
			SearchConfig searchConfig
		) {
			super();
			put(Elements.SHARED_PATH_SCANNER_CLASS_LOADER, sharedPathMemoryClassLoader);
			put(Elements.PATH_SCANNER_CLASS_LOADER, pathScannerClassLoader);
			put(Elements.SEARCH_CONFIG, searchConfig);
		}

		static InitContext create(
			PathScannerClassLoader sharedPathMemoryClassLoader,
			PathScannerClassLoader pathScannerClassLoader,
			SearchConfig searchConfig
		) {
			return new InitContext(sharedPathMemoryClassLoader, pathScannerClassLoader, searchConfig);
		}

		PathScannerClassLoader getSharedPathScannerClassLoader() {
			return get(Elements.SHARED_PATH_SCANNER_CLASS_LOADER);
		}

		PathScannerClassLoader getPathScannerClassLoader() {
			return get(Elements.PATH_SCANNER_CLASS_LOADER);
		}


		SearchConfig getSearchConfig() {
			return get(Elements.SEARCH_CONFIG);
		}
	}
}