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

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

import io.github.toolfactory.jvm.util.Strings;


public interface ClassPathScannerWithCachingSupport<I, R extends SearchResult<I>> extends ClassPathScanner<I, R>{
	
	public void clearCache();
	
	public CacheScanner<I, R> loadInCache(CacheableSearchConfig searchConfig);
	
	public R findAndCache();
	
	public R findBy(CacheableSearchConfig searchConfig);
	
	public void clearCache(boolean closeSearchResults);
	
	abstract class Abst<I, C extends SearchContext<I>, R extends SearchResult<I>> extends ClassPathScanner.Abst<I, C, R> {
		
		Map<String, Map<String, I>> cache;
		
		Abst(
			PathHelper pathHelper,
			Function<InitContext, C> contextSupplier,
			Function<C, R> resultSupplier,
			Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			Properties config
		) {
			super(
				pathHelper,
				contextSupplier,
				resultSupplier,
				defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
				config
			);
			this.cache = new ConcurrentHashMap<>();
		}
		
		public void clearCache() {
			clearCache(false);
		}
		
		public CacheScanner<I, R> loadInCache(CacheableSearchConfig searchConfig) {
			CacheableSearchConfig searchConfigCopy = searchConfig.isInitialized()? searchConfig : searchConfig.createCopy();
			if (!searchConfigCopy.isInitialized()) {
				searchConfigCopy.init(this);
			}
			CacheableSearchConfig flatSearchConfig = SearchConfig.forFileSystemItems(
				searchConfigCopy.getPathsToBeScanned()
			);
			try (R result = findBy(
				flatSearchConfig
			)){};
			return (srcCfg) -> 
				findBy(srcCfg == null? searchConfigCopy : srcCfg);
		}
		
		public R findAndCache() {
			return findBy(SearchConfig.create());
		}
	
		public R findBy(CacheableSearchConfig input) {
			SearchConfigAbst<?> searchConfig = input.isInitialized() ? input : input.createCopy();
			C context = searchConfig.isInitialized() ? searchConfig.getSearchContext() : searchConfig.init(this);
			if (!searchConfig.useDefaultPathScannerClassLoader) {
				return super.findBy(searchConfig);
			}
			context.executeSearch(() -> {
				Collection<FileSystemItem> pathsToBeScanned = searchConfig.getPathsToBeScanned();
				Collection<Map.Entry<FileSystemItem, Collection<FileSystemItem>>> classFilesForPath = ConcurrentHashMap.newKeySet();
				IterableObjectHelper.iterateParallelIf(
					pathsToBeScanned, 
					currentScannedPath -> {
						if (!currentScannedPath.isContainer()) {
							throw new IllegalArgumentException(Strings.compile("{} is not a folder or archive", currentScannedPath.getAbsolutePath()));
						}
						String currentScannedItemAbsolutePath = currentScannedPath.getAbsolutePath();
						if (searchConfig.getRefreshPathIf().test(currentScannedPath)) {
							Synchronizer.execute(instanceId + "_" + currentScannedItemAbsolutePath, () -> {
								Optional.ofNullable(cache.get(currentScannedItemAbsolutePath)).ifPresent((classesForPath) -> {
									cache.remove(currentScannedItemAbsolutePath);
									//classesForPath.clear();
								});
							});
						}
						Map<String, I> classesForPath = cache.get(currentScannedItemAbsolutePath);
						if (classesForPath != null && searchConfig.getClassCriteria().hasNoPredicate() &&
							!searchConfig.isFileFilterExternallySet() &&
							searchConfig.getFilesRetriever() != SearchConfigAbst.FIND_IN_CHILDREN
						) {
							context.addAllItemsFound(currentScannedItemAbsolutePath, classesForPath);
							return;
						}
						classFilesForPath.add(
							scanAndAddToPathScannerClassLoader(context, currentScannedPath)
						);
					},
					item -> item.size() > 1
				);
				IterableObjectHelper.iterateParallelIf(
					classFilesForPath,
					currentScannedPath -> {
						String currentScannedItemAbsolutePath = currentScannedPath.getKey().getAbsolutePath();
						Map<String, I> itemsForPath = cache.get(currentScannedItemAbsolutePath);
						if (itemsForPath != null) {							 
							analyzeAndAddCachedItemsToContext(context, currentScannedPath, itemsForPath);
						} else if (searchConfig.getClassCriteria().hasNoPredicate() &&
							!searchConfig.isFileFilterExternallySet() &&
							searchConfig.getFilesRetriever() != SearchConfigAbst.FIND_IN_CHILDREN) {
							Synchronizer.execute(instanceId + "_" + currentScannedItemAbsolutePath, () -> {
								Map<String, I> itemsForPathInternal = cache.get(currentScannedItemAbsolutePath);
								if (itemsForPathInternal == null) {
									analyzeAndAddItemsToContext(context, currentScannedPath);
									itemsForPathInternal = new ConcurrentHashMap<>();
									Map<String, I> itemsFound = context.getItemsFound(currentScannedItemAbsolutePath);
									if (itemsFound != null) {
										itemsForPathInternal.putAll(itemsFound);
									}
									this.cache.put(currentScannedItemAbsolutePath, itemsForPathInternal);
								} else {
									analyzeAndAddCachedItemsToContext(context, currentScannedPath, itemsForPathInternal);
								}
							});							
						} else {
							analyzeAndAddItemsToContext(context, currentScannedPath);
						}
					},
					item -> item.size() > 1
				);
				Collection<String> skippedClassesNames = context.getSkippedClassNames();
				if (!skippedClassesNames.isEmpty()) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Skipped classes count: {}", skippedClassesNames.size());
				}
			});
			R searchResult = resultSupplier.apply(context);
			searchResult.setClassPathScanner(this);
			return searchResult;
		}


		private void analyzeAndAddCachedItemsToContext(C context, Map.Entry<FileSystemItem, Collection<FileSystemItem>> currentScannedPath, Map<String, I> itemsForPath) {
			String currentScannedItemAbsolutePath = currentScannedPath.getKey().getAbsolutePath();
			for (FileSystemItem child : currentScannedPath.getValue()) {
				String childAbsolutePath = child.getAbsolutePath();
				I cachedItem = itemsForPath.get(childAbsolutePath);
				try {
					if(testCachedItem(context, cachedItem).getResult()) {
						context.addItemFound(
							currentScannedItemAbsolutePath,
							childAbsolutePath,cachedItem
						);
					}
				} catch (NullPointerException exc) {
					if (cachedItem != null) {
						throw exc;
					}
					ManagedLoggersRepository.logWarn(getClass()::getName, "Cached entry for path {} not found", childAbsolutePath);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not test cached entry for path {}", exc, childAbsolutePath);
				}
			}
		}

		
		public void clearCache(boolean closeSearchResults) {
			this.defaultPathScannerClassLoaderManager.reset();
			if (closeSearchResults) {
				closeSearchResults();
			}
			Collection<String> pathsToBeRemoved = new HashSet<>(cache.keySet());
			for (String path : pathsToBeRemoved) {
				Synchronizer.execute( instanceId + "_" + path, () -> {				
					FileSystemItem.ofPath(path).reset();
					Map<String, I> items = cache.remove(path);
					//clearItemsForPath(items);
				});
			}
		}

		abstract <S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(C context, I item);
	
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
	
	}
	
	@FunctionalInterface
	public static interface CacheScanner<I, R extends SearchResult<I>> {
		
		public R findBy(CacheableSearchConfig srcCfg);
		
		public default R find() {
			return findBy(null);
		}
	}
}