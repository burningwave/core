/*
 *This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: h*ttps://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019-2022 Roberto Gentili
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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Closeable;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemItem.Criteria;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;


@SuppressWarnings({"resource", "unchecked"})
public class SearchConfig implements Closeable {

	private final static BiFunction<Throwable, FileSystemItem[], Boolean> exceptionThrowerForFileFilter;


	Function<ClassLoader, Map.Entry<ClassLoader, Collection<FileSystemItem>>> pathsSupplier;
	Function<FileSystemItem, FileSystemItem.Find> findFunctionSupplier;
	Predicate<FileSystemItem> refreshPathIf;

	Boolean fileFiltersExtenallySet;
	Function<FileSystemItem, FileSystemItem.Criteria> fileFilterSupplier;
	Function<FileSystemItem, FileSystemItem.Criteria> additionalFileFilterSupplier;
	ClassCriteria classCriteria;

	Supplier<Collection<FileSystemItem>> pathsRetriever;
	SearchContext<?> searchContext;

	boolean useDefaultPathScannerClassLoader;
	boolean useDefaultPathScannerClassLoaderAsParent;
	ClassLoader parentClassLoaderForPathScannerClassLoader;
	PathScannerClassLoader pathScannerClassLoader;
	Predicate<Collection<?>> minimumCollectionSizeForParallelIterationPredicate;

	BiFunction<Throwable, FileSystemItem[], Boolean> fileFilterExceptionHandler;

	boolean waitForSearchEnding;
	Integer priority;
	boolean optimizePaths;

	static {
		exceptionThrowerForFileFilter = (exception, childAndParent) -> {
			ManagedLoggerRepository.logError(SearchConfig.class::getName, "Could not scan " + childAndParent[0].getAbsolutePath(), exception);
			return IterableObjectHelper.terminateIteration();
		};
	}

	SearchConfig() {
		pathsSupplier = classLoader -> {
			return new AbstractMap.SimpleEntry<>(classLoader, ConcurrentHashMap.newKeySet());
		};
		useDefaultPathScannerClassLoader(true);
		waitForSearchEnding = true;
		classCriteria = ClassCriteria.create();
		findFunctionSupplier = fileSystemItem -> FileSystemItem.Find.IN_ALL_CHILDREN;
	}

	public static SearchConfig create() {
		return new SearchConfig();
	}

	@SafeVarargs
	public static SearchConfig forPaths(Collection<String>... pathsColl) {
		return new SearchConfig().addPaths(pathsColl);
	}

	@SafeVarargs
	public static SearchConfig forFileSystemItems(Collection<FileSystemItem>... pathsColl) {
		return new SearchConfig().addFileSystemItems(pathsColl);
	}

	@SafeVarargs
	public static SearchConfig forPaths(String... paths) {
		return SearchConfig.forPaths((Collection<String>)Stream.of(paths).collect(Collectors.toCollection(HashSet::new)));
	}
	@SafeVarargs
	public static SearchConfig forResources(String... paths) {
		return forResources(null, paths);
	}

	@SafeVarargs
	public static SearchConfig forResources(ClassLoader classLoader, String... paths) {
		return forResources(classLoader, Arrays.asList(paths));
	}

	@SafeVarargs
	public static SearchConfig forResources(Collection<String>... pathCollections) {
		return forResources(null, pathCollections);
	}

	@SafeVarargs
	public static SearchConfig forResources(ClassLoader classLoader, Collection<String>... pathCollections) {
		return new SearchConfig().addResources(classLoader, pathCollections);
	}

	public static SearchConfig byCriteria(ClassCriteria classCriteria) {
		return forPaths(new HashSet<>()).by(classCriteria);
	}


	public SearchConfig by(ClassCriteria classCriteria) {
		this.classCriteria = classCriteria;
		return this;
	}

	<I, C extends SearchContext<I>> C init(ClassPathScanner.Abst<I, C, ?> classPathScanner) {
		if (fileFilterSupplier == null) {
			fileFiltersExtenallySet = additionalFileFilterSupplier != null;
			fileFilterSupplier = fileSystemItem -> FileSystemItem.Criteria.forClassTypeFiles(
				IterableObjectHelper.resolveStringValue(
					ResolveConfig.forNamedKey(classPathScanner.getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties())
					.on(classPathScanner.config)
				)
			);
		} else {
			fileFiltersExtenallySet = Boolean.TRUE;
		}
		PathScannerClassLoader pathScannerClassLoader = this.pathScannerClassLoader;
		PathScannerClassLoader defaultPathScannerClassLoader = classPathScanner.getDefaultPathScannerClassLoader(this);
		if (pathScannerClassLoader == null) {
			if (useDefaultPathScannerClassLoaderAsParent) {
				parentClassLoaderForPathScannerClassLoader = defaultPathScannerClassLoader;
			}
			pathScannerClassLoader = useDefaultPathScannerClassLoader ?
				defaultPathScannerClassLoader :
				PathScannerClassLoader.create(
					parentClassLoaderForPathScannerClassLoader,
					classPathScanner.pathHelper,
					null
				);
		}
		C context = classPathScanner.contextSupplier.apply(
			InitContext.create(
				defaultPathScannerClassLoader,
				pathScannerClassLoader,
				this
			)
		);
		if (classCriteria != null) {
			classCriteria.init(context.pathScannerClassLoader);
		}
		PathScannerClassLoader finalPathScannerClassLoader = pathScannerClassLoader;
		pathsRetriever = () -> {
			Collection<FileSystemItem> pathsToBeScanned = pathsSupplier.apply(finalPathScannerClassLoader).getValue();
			if (pathsToBeScanned.isEmpty()) {
				ManagedLoggerRepository.logInfo(getClass()::getName, "The input paths are not present: the search will be performed on the default configured paths");
				pathsToBeScanned.addAll(classPathScanner.pathHelper.getPaths(ClassPathScanner.Configuration.Key.DEFAULT_SEARCH_CONFIG_PATHS)
					.stream().map(FileSystemItem::ofPath).collect(Collectors.toSet()));
			}
			if (optimizePaths && findFunctionSupplier != FileSystemItem.Find.FunctionSupplier.OF_IN_CHILDREN && !fileFiltersExtenallySet) {
				if (findFunctionSupplier != FileSystemItem.Find.FunctionSupplier.OF_IN_ALL_CHILDREN  &&
					findFunctionSupplier != FileSystemItem.Find.FunctionSupplier.OF_RECURSIVE_IN_CHILDREN) {
					throw new IllegalArgumentException("Could not optimize paths with custom find function supplier");
				}
				classPathScanner.pathHelper.optimizeFileSystemItems(pathsToBeScanned);
			}
			return pathsToBeScanned;
		};
		if (refreshPathIf == null) {
			refreshPathIf = fileSystemItem -> false;
		}
		searchContext = context;
		defaultPathScannerClassLoader.unregister(this, true);
		return context;
	}

	@SafeVarargs
	public final SearchConfig addPaths(Collection<String>... pathColls) {
		for (Collection<String> pathColl : pathColls) {
			pathsSupplier = pathsSupplier.andThen(classLoaderAndPaths -> {
				for (String absolutePath : pathColl) {
					try {
						classLoaderAndPaths.getValue().add(FileSystemItem.ofPath(absolutePath));
					} catch (Throwable exc) {
						throw new IllegalArgumentException("One or more of the input paths are incorrect", exc);
					}
				}
				return classLoaderAndPaths;
			});
		}
		return this;
	}

	public SearchConfig addPaths(String... paths) {
		return addPaths(Arrays.asList(paths));
	}

	@SafeVarargs
	public final SearchConfig addFileSystemItems(Collection<FileSystemItem>... pathColls) {
		for (Collection<FileSystemItem> pathColl : pathColls) {
			pathsSupplier = pathsSupplier.andThen(classLoaderAndPaths -> {
				for (FileSystemItem absolutePath : pathColl) {
					if (absolutePath == null) {
						throw new IllegalArgumentException("One of the input resources is null");
					}
					classLoaderAndPaths.getValue().add(absolutePath);
				}
				return classLoaderAndPaths;
			});
		}
		return this;
	}

	@SafeVarargs
	public final SearchConfig addFileSystemItems(FileSystemItem... paths) {
		return addFileSystemItems(Arrays.asList(paths));
	}

	@SafeVarargs
	public final SearchConfig addResources(ClassLoader classLoader, Collection<String>... pathColls) {
		for (Collection<String> pathColl : pathColls) {
			pathsSupplier = pathsSupplier.andThen(classLoaderAndPaths -> {
				Collection<FileSystemItem> resources = Resources.getAsFileSystemItems(
					classLoader != null? classLoader : classLoaderAndPaths.getKey(),
					pathColl
				);
				if (resources.isEmpty()) {
					throw new IllegalArgumentException("One or more of the input resources are incorrect");
				}
				classLoaderAndPaths.getValue().addAll(resources	);
				return classLoaderAndPaths;
			});
		}
		return this;
	}

	@SafeVarargs
	public final SearchConfig addResources(ClassLoader classLoader, String... paths) {
		return addResources(classLoader, Arrays.asList(paths));
	}

	@SafeVarargs
	public final SearchConfig addResources(String... paths) {
		return addResources(Arrays.asList(paths));
	}

	@SafeVarargs
	public final SearchConfig addResources(Collection<String>... pathCollections) {
		return addResources(null, pathCollections);
	}

	public SearchConfig withPriority(Integer priority) {
		this.priority = priority;
		return this;
	}

	public SearchConfig waitForSearchEnding(boolean waitForSearchEnding) {
		this.waitForSearchEnding = waitForSearchEnding;
		return this;
	}

	public SearchConfig checkForAddedClassesForAllPathThat(Predicate<FileSystemItem> refreshIf) {
		if (refreshPathIf == null) {
			refreshPathIf = refreshIf;
		} else {
			refreshPathIf = refreshPathIf.or(refreshIf);
		}
		return this;
	}

	public SearchConfig checkForAddedClasses() {
		this.refreshPathIf = FileSystemItem -> true;
		return this;
	}

	public SearchConfig optimizePaths(boolean flag) {
		this.optimizePaths = flag;
		return this;
	}

	public SearchConfig setFindFunction(Function<FileSystemItem, FileSystemItem.Find> findInFunction) {
		this.findFunctionSupplier = findInFunction;
		return this;
	}

	public SearchConfig findInChildren() {
		findFunctionSupplier = FileSystemItem.Find.FunctionSupplier.OF_IN_CHILDREN;
		return this;
	}

	public SearchConfig findRecursiveInChildren() {
		findFunctionSupplier = FileSystemItem.Find.FunctionSupplier.OF_RECURSIVE_IN_CHILDREN;
		return this;
	}

	public SearchConfig findInAllChildren() {
		findFunctionSupplier = FileSystemItem.Find.FunctionSupplier.OF_IN_ALL_CHILDREN;
		return this;
	}

	public SearchConfig findFirstInAllChildren() {
		findFunctionSupplier = FileSystemItem.Find.FunctionSupplier.OF_FIRST_IN_ALL_CHILDREN;
		return this;
	}

	public SearchConfig findFirstInChildren() {
		findFunctionSupplier = FileSystemItem.Find.FunctionSupplier.OF_FIRST_IN_CHILDREN;
		return this;
	}

	public SearchConfig setFileFilter(Function<FileSystemItem, FileSystemItem.Criteria> filterSupplier) {
		this.fileFilterSupplier = filterSupplier;
		return this;
	}

	public SearchConfig setFileFilter(FileSystemItem.Criteria filter) {
		this.fileFilterSupplier = fileSystemItem -> filter;
		return this;
	}

	public SearchConfig addFileFilter(Function<FileSystemItem, FileSystemItem.Criteria> filterSupplier) {
		if (additionalFileFilterSupplier == null) {
			additionalFileFilterSupplier = filterSupplier;
			return this;
		}
		Function<FileSystemItem, FileSystemItem.Criteria> previousAdditionalFileFilterSupplier = additionalFileFilterSupplier;
		additionalFileFilterSupplier = fileSystemItem -> previousAdditionalFileFilterSupplier.apply(fileSystemItem).and(filterSupplier.apply(fileSystemItem));
		return this;
	}

	public SearchConfig addFileFilter(FileSystemItem.Criteria filter) {
		if (additionalFileFilterSupplier == null) {
			additionalFileFilterSupplier = fileSystemItem -> filter;
			return this;
		}
		Function<FileSystemItem, FileSystemItem.Criteria> previousAdditionalFileFilterSupplier = additionalFileFilterSupplier;
		additionalFileFilterSupplier = fileSystemItem -> previousAdditionalFileFilterSupplier.apply(fileSystemItem).and(filter);
		return this;
	}

	public SearchConfig withExceptionHandlerForFileFilter(BiFunction<Throwable, FileSystemItem[], Boolean> fileFilterExceptionHandler) {
		if (fileFilterExceptionHandler != null) {
			this.fileFilterExceptionHandler = fileFilterExceptionHandler;
		} else {
			this.fileFilterExceptionHandler = exceptionThrowerForFileFilter;
		}
		return this;
	}

	public SearchConfig setMinimumCollectionSizeForParallelIteration(int value) {
		this.minimumCollectionSizeForParallelIterationPredicate = collection -> collection.size() >= value;
		return this;
	}

	public SearchConfig useClassLoader(PathScannerClassLoader classLoader) {
		if (classLoader == null)  {
			org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("Class loader could not be null");
		}
		useDefaultPathScannerClassLoader = false;
		useDefaultPathScannerClassLoaderAsParent = false;
		parentClassLoaderForPathScannerClassLoader = null;
		pathScannerClassLoader = classLoader;
		return this;
	}

	public SearchConfig useDefaultPathScannerClassLoader(boolean value) {
		useDefaultPathScannerClassLoader = value;
		useDefaultPathScannerClassLoaderAsParent = !useDefaultPathScannerClassLoader;
		parentClassLoaderForPathScannerClassLoader = null;
		pathScannerClassLoader = null;
		return this;
	}

	public SearchConfig useAsParentClassLoader(ClassLoader classLoader) {
		if (classLoader == null)  {
			org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("Parent class loader could not be null");
		}
		useDefaultPathScannerClassLoader = false;
		useDefaultPathScannerClassLoaderAsParent = false;
		parentClassLoaderForPathScannerClassLoader = classLoader;
		pathScannerClassLoader = null;
		return this;
	}

	public SearchConfig useDefaultPathScannerClassLoaderAsParent(boolean value) {
		useDefaultPathScannerClassLoaderAsParent = value;
		useDefaultPathScannerClassLoader = !useDefaultPathScannerClassLoaderAsParent;
		parentClassLoaderForPathScannerClassLoader = null;
		pathScannerClassLoader = null;
		return this;
	}

	public SearchConfig useNewIsolatedClassLoader() {
		useDefaultPathScannerClassLoaderAsParent = false;
		useDefaultPathScannerClassLoader = false;
		parentClassLoaderForPathScannerClassLoader = null;
		pathScannerClassLoader = null;
		return this;
	}

	ClassCriteria getClassCriteria() {
		return this.classCriteria;
	}

	Collection<FileSystemItem> getPathsToBeScanned() {
		return pathsRetriever.get();
	}

	BiFunction<FileSystemItem, Criteria, Collection<FileSystemItem>> getFindFunction(FileSystemItem fileSystemItem) {
		return findFunctionSupplier.apply(fileSystemItem);
	}

	Predicate<FileSystemItem> getRefreshPathIf() {
		return this.refreshPathIf;
	}

	FileSystemItem.Criteria getAllFileFilters(FileSystemItem currentScannedPath){
		FileSystemItem.Criteria fileFilter = null;
		if (additionalFileFilterSupplier != null) {
			fileFilter = fileFilterSupplier.apply(currentScannedPath).and(additionalFileFilterSupplier.apply(currentScannedPath));
		} else {
			fileFilter = fileFilterSupplier.apply(currentScannedPath);
		}
		if (fileFilter.getMinimumCollectionSizeForParallelIterationPredicate() == null) {
			fileFilter.setMinimumCollectionSizeForParallelIteration(
				this.minimumCollectionSizeForParallelIterationPredicate
			);
		}
		if (fileFilter.getPriority() == null) {
			fileFilter.withPriority(this.priority);
		}
		if (fileFilter.getExceptionHandler() == null) {
			if (this.fileFilterExceptionHandler != null) {
				fileFilter.setExceptionHandler(this.fileFilterExceptionHandler);
			} else {
				fileFilter.enableDefaultExceptionHandler();
			}
		}
		return fileFilter;
	}

	Predicate<Collection<?>> getMinimumCollectionSizeForParallelIterationPredicate() {
		return minimumCollectionSizeForParallelIterationPredicate;
	}

	boolean isFileFilterExternallySet() {
		return fileFiltersExtenallySet;
	}


	boolean isInitialized() {
		return pathsRetriever != null && searchContext != null;
	}

	<I, C extends SearchContext<I>> C getSearchContext() {
		return (C)searchContext;
	}

	public SearchConfig copyTo(SearchConfig destConfig) {
		destConfig.classCriteria = this.classCriteria.createCopy();
		destConfig.pathsRetriever = this.pathsRetriever;
		destConfig.findFunctionSupplier = this.findFunctionSupplier;
		destConfig.refreshPathIf = this.refreshPathIf;
		destConfig.fileFilterSupplier = this.fileFilterSupplier;
		destConfig.additionalFileFilterSupplier = this.additionalFileFilterSupplier;
		destConfig.pathsSupplier = this.pathsSupplier;
		destConfig.optimizePaths = this.optimizePaths;
		destConfig.useDefaultPathScannerClassLoader = this.useDefaultPathScannerClassLoader;
		destConfig.parentClassLoaderForPathScannerClassLoader = this.parentClassLoaderForPathScannerClassLoader;
		destConfig.pathScannerClassLoader = this.pathScannerClassLoader;
		destConfig.useDefaultPathScannerClassLoaderAsParent = this.useDefaultPathScannerClassLoaderAsParent;
		destConfig.waitForSearchEnding = this.waitForSearchEnding;
		destConfig.priority = this.priority;
		destConfig.minimumCollectionSizeForParallelIterationPredicate = this.minimumCollectionSizeForParallelIterationPredicate;
		destConfig.fileFilterExceptionHandler = this.fileFilterExceptionHandler;
		return destConfig;
	}

	public SearchConfig createCopy() {
		return copyTo(new SearchConfig());
	}

	@Override
	public void close() {
		this.classCriteria.close();
		pathsRetriever = null;
		findFunctionSupplier = null;
		refreshPathIf = null;
		fileFilterSupplier = null;
		additionalFileFilterSupplier = null;
		pathsSupplier = null;
		parentClassLoaderForPathScannerClassLoader = null;
		pathScannerClassLoader = null;
		priority = null;
		minimumCollectionSizeForParallelIterationPredicate = null;
		fileFilterExceptionHandler = null;
	}
}
