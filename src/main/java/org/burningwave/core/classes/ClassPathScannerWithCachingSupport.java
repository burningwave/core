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

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.ClassFileScanConfig;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.PathHelper.ComparePathsResult;


abstract class ClassPathScannerWithCachingSupport<I, C extends SearchContext<I>, R extends SearchResult<I>> extends ClassPathScannerAbst<I, C, R> {
	Map<String, Map<String, I>> cache;

	ClassPathScannerWithCachingSupport(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemScanner,
			pathHelper,
			contextSupplier,
			resultSupplier
		);
		this.cache = new HashMap<>();
	}
	
	public CacheScanner<I, R> loadCache(CacheableSearchConfig searchConfig) {
		try (R result = findBy(
			SearchConfig.forPaths(
				searchConfig.getPaths()
			).optimizePaths(
				true
			).checkFileOptions(
				searchConfig.getCheckFileOptions()
			)
		)){};
		return () -> findBy(searchConfig);
	}
	
	//Cached search
	public R findBy(CacheableSearchConfig searchConfig) {
		searchConfig = searchConfig.createCopy();
		C context = createContext(
			ClassFileScanConfig.forPaths(searchConfig.getPaths()).checkFileOptions(searchConfig.getCheckFileOptions()).maxParallelTasksForUnit(
				searchConfig.maxParallelTasksForUnit
			), 
			searchConfig
		);
		searchConfig.init(context.pathMemoryClassLoader);
		context.executeSearch(() ->
			scan(context)
		);
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		return resultSupplier.apply(context);
	}
	
	
	void scan(C context) {
		Collection<String> pathsNotScanned = scanCache(context);
		if (!pathsNotScanned.isEmpty()) {
			if (context.getSearchConfig().getClassCriteria().hasNoPredicate()) {
				synchronized (cache) {
					pathsNotScanned = scanCache(context);
					if (!pathsNotScanned.isEmpty()) {
						for (String path : pathsNotScanned) {
							Map<String, I> classesForPath = cache.get(path);
							if (classesForPath != null && !classesForPath.isEmpty()) {
								context.addAllItemsFound(path,classesForPath);
								pathsNotScanned.remove(path);
							}
						}
						if (!pathsNotScanned.isEmpty()) {
							loadCache(context, pathsNotScanned);
						}
					}
				}
			} else {
				fileSystemScanner.scan(
					context.classFileScanConfiguration.createCopy().setPaths(pathsNotScanned).toScanConfiguration(
						getFileSystemEntryTransformer(context),
						getZipEntryTransformer(context)
					)				
				);
			}
		}
	}
	
	Collection<String> scanCache(C context) {
		Collection<String> pathsNotScanned = new LinkedHashSet<>();
		CacheableSearchConfig searchConfig = context.getSearchConfig();
		if (!context.getSearchConfig().getClassCriteria().hasNoPredicate()) {
			for (String path : searchConfig.getPaths()) {
				Map<String, I> classesForPath = cache.get(path);
				if (classesForPath != null) {
					if (!classesForPath.isEmpty()) {	
						iterateAndTestCachedItemsForPath(context, path, classesForPath);
					}
				} else {
					pathsNotScanned.add(path);
				}
			}
		} else {
			for (String path : searchConfig.getPaths()) {
				Map<String, I> classesForPath = cache.get(path);
				if (classesForPath != null) {
					if (!classesForPath.isEmpty()) {
						context.addAllItemsFound(path, classesForPath);
					}
				} else {
					pathsNotScanned.add(path);
				}
			}
		}
		return pathsNotScanned;
	}

	
	public void clearCache() {
		cache.entrySet().stream().forEach(entry -> {
			entry.getValue().clear();
		});
		cache.clear();
	}

	void loadCache(C context, Collection<String> paths) {
		ComparePathsResult checkPathsResult = pathHelper.comparePaths(cache.keySet(), paths);
		ClassFileScanConfig classFileScanConfiguration = context.classFileScanConfiguration.createCopy().setPaths(checkPathsResult.getNotContainedPaths());
		Map<String, Map<String, I>> tempCache = new LinkedHashMap<>();
		if (!checkPathsResult.getPartialContainedDirectories().isEmpty()) {
			Predicate<File> directoryPredicate = null;
			for (Entry<String, Collection<String>> entry : checkPathsResult.getPartialContainedDirectories().entrySet()) {
				for (String path : entry.getValue()) {
					tempCache.put(entry.getKey(), cache.get(path));
					if (directoryPredicate != null) {
						directoryPredicate.and(file -> !(Paths.clean(file.getAbsolutePath()) + "/").startsWith(Paths.clean(path) + "/"));
					} else {
						directoryPredicate = file -> !(Paths.clean(file.getAbsolutePath()) + "/").startsWith(Paths.clean(path) + "/");
					}
				}
			}
			if (directoryPredicate != null) {
				classFileScanConfiguration.scanRecursivelyAllDirectoryThat(directoryPredicate);
			}
		}
		if (!checkPathsResult.getPartialContainedFiles().isEmpty()) {
			Predicate<File> filePredicate = null;
			for (Entry<String, Collection<String>> entry : checkPathsResult.getPartialContainedFiles().entrySet()) {
				for (String path : entry.getValue()) {
					tempCache.put(Paths.clean(entry.getKey()), cache.get(path));
					if (filePredicate != null) {
						filePredicate.and(file -> !(Paths.clean(file.getAbsolutePath())).equals(Paths.clean(path)));
					} else {
						filePredicate = file -> !(Paths.clean(file.getAbsolutePath())).equals(Paths.clean(path));
					}
				}
			}
			if (filePredicate != null) {
				classFileScanConfiguration.scanAllArchiveFileThat(filePredicate);
				classFileScanConfiguration.scanAllFileThat(filePredicate);
			}
		}
		if (!checkPathsResult.getContainedPaths().isEmpty()) {
			for (Entry<String, Collection<String>> entry : checkPathsResult.getContainedPaths().entrySet()) {
				classFileScanConfiguration.addPaths(entry.getValue().stream().map(path -> Paths.clean(path)).collect(Collectors.toList()));
			}
		}
		
		fileSystemScanner.scan(
			classFileScanConfiguration.toScanConfiguration(
				getFileSystemEntryTransformer(context),
				getZipEntryTransformer(context)
			).afterScanPath((mainScanContext, path) -> {
				mainScanContext.waitForTasksEnding();
				Map<String, I> itemsForPath = new HashMap<>();
				Map<String, I> itemsFound = context.getItemsFound(path);
				if (itemsFound != null) {
					itemsForPath.putAll(itemsFound);
				}
				this.cache.put(path, itemsForPath);
			})
		);
		if (!tempCache.isEmpty()) {
			for (Entry<String, Map<String, I>> entry : tempCache.entrySet()) {
				cache.get(entry.getKey()).putAll(entry.getValue());
				context.addAllItemsFound(entry.getKey(), entry.getValue());
			}
		}
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
		
		R find();
		
	}
}