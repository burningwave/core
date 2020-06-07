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
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.concurrent.Mutex;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;


public abstract class ClassPathScannerWithCachingSupport<I, C extends SearchContext<I>, R extends SearchResult<I>> extends ClassPathScannerAbst<I, C, R> {
	Map<String, Map<String, I>> cache;
	Mutex.Manager mutexManager;
	
	ClassPathScannerWithCachingSupport(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			pathHelper,
			contextSupplier,
			resultSupplier
		);
		this.cache = new HashMap<>();
		this.mutexManager = Mutex.Manager.create();
	}

	public CacheScanner<I, R> loadInCache(CacheableSearchConfig searchConfig) {
		try (R result = findBy(
			SearchConfig.forPaths(
				searchConfig.getPaths()
			).optimizePaths(
				true
			).checkFileOptions(
				searchConfig.getCheckFileOptions()
			)
		)){};
		return (srcCfg) -> 
			findBy(srcCfg == null? searchConfig : srcCfg);
	}
	
	//Cached search
	public R findBy(CacheableSearchConfig searchConfig) {
		return findBy(searchConfig, this::searchInCacheOrInFileSystem);
	}
	
	void searchInCacheOrInFileSystem(C context) {
		BiPredicate<FileSystemItem, FileSystemItem> filter = getTestItemPredicate(context);
		context.getSearchConfig().getPaths().parallelStream().forEach(path -> {
			Map<String, I> classesForPath = cache.get(path);
			if (classesForPath == null) {
				if (context.getSearchConfig().getClassCriteria().hasNoPredicate()) {
					synchronized(mutexManager.getMutex(path)) {
						classesForPath = cache.get(path);
						if (classesForPath == null) {
							FileSystemItem.ofPath(path).getAllChildren(filter);
							Map<String, I> itemsForPath = new HashMap<>();
							Map<String, I> itemsFound = context.getItemsFound(path);
							if (itemsFound != null) {
								itemsForPath.putAll(itemsFound);
							}
							this.cache.put(path, itemsForPath);
						} else {
							context.addAllItemsFound(path, classesForPath);
						}
					}
				} else {
					FileSystemItem.ofPath(path).getAllChildren(filter);
					context.getItemsFoundFlatMap();
				}
			} else if (context.getSearchConfig().getClassCriteria().hasNoPredicate()) {
				context.addAllItemsFound(path, classesForPath);
			} else {
				if (!classesForPath.isEmpty()) {	
					iterateAndTestCachedItemsForPath(context, path, classesForPath);
				}
			}
		});
	}

	<S extends SearchConfigAbst<S>> void iterateAndTestCachedItemsForPath(C context, String path, Map<String, I> itemsForPath) {
		for (Entry<String, I> cachedItemAsEntry : itemsForPath.entrySet()) {
			ClassCriteria.TestContext testContext = testCachedItem(context, path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue());
			if(testContext.getResult()) {
				addCachedItemToContext(context, testContext, path, cachedItemAsEntry);
			}
		}
	}
	
	
	<S extends SearchConfigAbst<S>> void addCachedItemToContext(
		C context, ClassCriteria.TestContext testContext, String path, Entry<String, I> cachedItemAsEntry
	) {
		context.addItemFound(path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue());
	}

	abstract <S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(C context, String path, String key, I value);
	
	public void clearCache() {
		Collection<String> pathsToBeRemoved = new HashSet<>(cache.keySet());
		for (String path : pathsToBeRemoved) {
			synchronized(mutexManager.getMutex(path)) {
				FileSystemItem.ofPath(path).reset();
				Map<String, I> items = cache.remove(path);
				if (items != null) {
					items.clear();
				}
			}
		}
	}
	
	@Override
	public void close() {
		clearCache();
		cache = null;
		byteCodeHunterSupplier = null;
		pathHelper = null;
		contextSupplier = null;
	}
	
	@FunctionalInterface
	public static interface CacheScanner<I, R extends SearchResult<I>> {
		
		public R findBy(CacheableSearchConfig srcCfg);
		
		public default R find() {
			return findBy(null);
		}
	}
}