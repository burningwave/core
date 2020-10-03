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
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.concurrent.Synchronizer.Mutex;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;


public abstract class ClassPathScannerWithCachingSupport<I, C extends SearchContext<I>, R extends SearchResult<I>> extends ClassPathScannerAbst<I, C, R> {
	
	Map<String, Map<String, I>> cache;
	
	ClassPathScannerWithCachingSupport(
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier, 
		Properties config
	) {
		super(
			classHunterSupplier,
			pathHelper,
			contextSupplier,
			resultSupplier,
			config
		);
		this.cache = new ConcurrentHashMap<>();
	}

	public CacheScanner<I, R> loadInCache(CacheableSearchConfig searchConfig) {
		try (R result = findBy(
			SearchConfig.forPaths(
				searchConfig.getPaths()
			)
		)){};
		return (srcCfg) -> 
			findBy(srcCfg == null? searchConfig : srcCfg);
	}
	
	public R findAndCache() {
		return findBy(SearchConfig.create());
	}

	public R findBy(CacheableSearchConfig searchConfig) {
		return findBy(searchConfig, this::searchInCacheOrInFileSystem);
	}
	
	void searchInCacheOrInFileSystem(C context) {
		CacheableSearchConfig searchConfig = context.getSearchConfig();
		boolean scanFileCriteriaHasNoPredicate = searchConfig.getScanFileCriteria().hasNoPredicate();
		boolean classCriteriaHasNoPredicate = searchConfig.getClassCriteria().hasNoPredicate();		
		FileSystemItem.Criteria filterAndExecutor = buildFileAndClassTesterAndExecutor(context);
		//scanFileCriteria in this point has been changed by the previous method call
		FileSystemItem.Criteria fileFilter = searchConfig.getScanFileCriteria();
		IterableObjectHelper.iterateParallelIf(
			context.getSearchConfig().getPaths(), 
			basePath -> {
				searchInCacheOrInFileSystem(
					basePath, context,
					scanFileCriteriaHasNoPredicate, classCriteriaHasNoPredicate, filterAndExecutor, fileFilter
				);
			},
			item -> item.size() > 1
		);		
	}

	private void searchInCacheOrInFileSystem(
		String basePath,
		C context,
		boolean scanFileCriteriaHasNoPredicate,
		boolean classCriteriaHasNoPredicate,
		FileSystemItem.Criteria filterAndExecutor,
		FileSystemItem.Criteria fileFilter
	) {
		CacheableSearchConfig searchConfig = context.getSearchConfig();
		FileSystemItem currentScannedPath;
		currentScannedPath = FileSystemItem.ofPath(basePath);
		Predicate<String> refreshCache = searchConfig.getCheckForAddedClassesPredicate();
		if (refreshCache != null && refreshCache.test(basePath)) {
			Synchronizer.execute(instanceId + "_" + basePath, () -> {
				Optional.ofNullable(cache.get(basePath)).ifPresent((classesForPath) -> {
					cache.remove(basePath);
					classesForPath.clear();
				});
			});
			currentScannedPath.refresh();
		}
		Map<String, I> classesForPath = cache.get(basePath);
		if (classesForPath == null) {
			if (classCriteriaHasNoPredicate && scanFileCriteriaHasNoPredicate) {
				String mutexId = instanceId + "_" + basePath;
				boolean exit = false;
				Mutex mutex = Synchronizer.getMutex(mutexId);
				synchronized(mutex) {
					classesForPath = cache.get(basePath);
					if (classesForPath == null) {
						currentScannedPath.findInAllChildren(filterAndExecutor);
						Map<String, I> itemsForPath = new ConcurrentHashMap<>();
						Map<String, I> itemsFound = context.getItemsFound(basePath);
						if (itemsFound != null) {
							itemsForPath.putAll(itemsFound);
						}
						this.cache.put(basePath, itemsForPath);	
						exit = true;
					}
				}
				Synchronizer.removeMutexIfUnused(mutex);
				if (exit) {
					return;
				}
				context.addAllItemsFound(basePath, classesForPath);
				return;
			} else {
				currentScannedPath.findInAllChildren(filterAndExecutor);
				return;
			}
		}
		if (classCriteriaHasNoPredicate && scanFileCriteriaHasNoPredicate) {
			context.addAllItemsFound(basePath, classesForPath);
		} else if (scanFileCriteriaHasNoPredicate) {
			iterateAndTestCachedItems(context, basePath, classesForPath);
		} else if (classCriteriaHasNoPredicate) {
			iterateAndTestCachedPaths(context, basePath, classesForPath, fileFilter);
		} else {
			iterateAndTestCachedPathsAndItems(context, basePath, classesForPath, fileFilter);
		}
	}
	
	void iterateAndTestCachedPaths(
		C context,
		String basePath,
		Map<String, I> itemsForPath,
		FileSystemItem.Criteria fileFilter
	) {
		FileSystemItem basePathFSI = FileSystemItem.ofPath(basePath);
		FileSystemItem[] currentChildPathAndBasePath = new FileSystemItem[]{
			null,
			basePathFSI
		};
		Predicate<FileSystemItem[]> fileFilterPredicate = fileFilter.getPredicateOrTruePredicateIfPredicateIsNull();
		for (Entry<String, I> cachedItemAsEntry : itemsForPath.entrySet()) {
			String absolutePathOfItem = cachedItemAsEntry.getKey();
			try {				
				currentChildPathAndBasePath[0] = FileSystemItem.ofPath(absolutePathOfItem);
				if (fileFilterPredicate.test(currentChildPathAndBasePath)) {
					context.addItemFound(basePath, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue());
				}
			} catch (Throwable exc) {
				logError("Could not test cached entry of path " + absolutePathOfItem, exc);
			}
		}
	}

	final <S extends SearchConfigAbst<S>> void iterateAndTestCachedPathsAndItems(
		C context, 
		String basePath,
		Map<String, I>itemsForPath,
		FileSystemItem.Criteria fileFilter
	) {
		FileSystemItem basePathFSI = FileSystemItem.ofPath(basePath);
		FileSystemItem[] currentChildPathAndBasePath = new FileSystemItem[]{
			null,
			basePathFSI
		};
		Predicate<FileSystemItem[]> fileFilterPredicate = fileFilter.getPredicateOrTruePredicateIfPredicateIsNull();
		for (Entry<String, I> cachedItemAsEntry : itemsForPath.entrySet()) {
			String absolutePathOfItem = cachedItemAsEntry.getKey();
			try {
				currentChildPathAndBasePath[0] = FileSystemItem.ofPath(absolutePathOfItem);
				ClassCriteria.TestContext testContext;
				if((testContext = testPathAndCachedItem(
					context, currentChildPathAndBasePath, cachedItemAsEntry.getValue(), fileFilterPredicate
				)).getResult()) {
					addCachedItemToContext(context, testContext, basePath, cachedItemAsEntry);
				}
			} catch (Throwable exc) {
				logError("Could not test cached entry of path " + absolutePathOfItem, exc);
			}
		}
	}

	void iterateAndTestCachedItems(C context, String basePath, Map<String, I> itemsForPath) {
		for (Entry<String, I> cachedItemAsEntry : itemsForPath.entrySet()) {
			String absolutePathOfItem = cachedItemAsEntry.getKey();
			try {
				ClassCriteria.TestContext testContext = testCachedItem(context, basePath, absolutePathOfItem, cachedItemAsEntry.getValue());
				if(testContext.getResult()) {
					addCachedItemToContext(context, testContext, basePath, cachedItemAsEntry);
				}
			} catch (Throwable exc) {
				logError("Could not test cached entry of path " + absolutePathOfItem, exc);
			}
		}
	}
	
	TestContext testPath(
		C context, 
		FileSystemItem[] filesToBeTested, 
		Predicate<FileSystemItem[]> fileFilterPredicate
	) {
		if (fileFilterPredicate.test(filesToBeTested)) {
			return context.getSearchConfig().getClassCriteria().testWithTrueResultForNullEntityOrTrueResultForNullPredicate(null);
		}
		return context.test(null);
	}
	
	TestContext testPathAndCachedItem(
		C context, 
		FileSystemItem[] filesToBeTested, 
		I item,
		Predicate<FileSystemItem[]> fileFilterPredicate
	) {
		if (fileFilterPredicate.test(filesToBeTested)) {
			return testCachedItem(context, filesToBeTested[1].getAbsolutePath(), filesToBeTested[0].getAbsolutePath(), item);
		}
		return context.test(null);
	}

	<S extends SearchConfigAbst<S>> void addCachedItemToContext(
		C context, ClassCriteria.TestContext testContext, String path, Entry<String, I> cachedItemAsEntry
	) {
		context.addItemFound(path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue());
	}

	abstract <S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(C context, String basePath, String absolutePathOfItem, I item);
	
	public void clearCache() {
		clearCache(false);
	}
	
	public void clearCache(boolean closeSearchResults) {
		ClassHunter classHunter = this.classHunter;
		//this check is necessary to avoid infinite recursion
		if (classHunter != null && this != classHunter && !classHunter.isClosed()) {
			try {
				//clearing the cache and resetting the class loader (owned  by the ClassHunter)
				classHunter.clearCache(closeSearchResults);
			} catch (Throwable exc) {
				logWarn("Exception occurred while trying to clear the cache of {}: {}", classHunter, exc.getMessage());
			}
		}
		if (closeSearchResults) {
			closeSearchResults();
		}
		Collection<String> pathsToBeRemoved = new HashSet<>(cache.keySet());
		for (String path : pathsToBeRemoved) {
			Synchronizer.execute( instanceId + "_" + path, () -> {				
				FileSystemItem.ofPath(path).reset();
				Map<String, I> items = cache.remove(path);
				clearItemsForPath(items);
			});
		}
	}

	void clearItemsForPath(Map<String, I> items) {
		if (items != null) {
			items.clear();
		}
	}
	
	boolean isClosed() {
		return cache == null;
	}
	
	@Override
	public void close() {
		clearCache(false);
		cache = null;
		pathHelper = null;
		contextSupplier = null;
		super.close();
	}
	
	@FunctionalInterface
	public static interface CacheScanner<I, R extends SearchResult<I>> {
		
		public R findBy(CacheableSearchConfig srcCfg);
		
		public default R find() {
			return findBy(null);
		}
	}
}