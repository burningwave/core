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


import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Closeable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemItem.Criteria;


@SuppressWarnings("unchecked")
abstract class SearchConfigAbst<S extends SearchConfigAbst<S>> implements Closeable, ManagedLogger {
	final static BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> FIND_IN_CHILDREN = FileSystemItem::findInChildren;
	final static BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> FIND_RECURSIVE_IN_CHILDREN = FileSystemItem::findRecursiveInChildren;
	final static BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> FIND_IN_ALL_CHILDREN = FileSystemItem::findInAllChildren;
	
	
	Function<ClassLoader, Map.Entry<ClassLoader, Collection<FileSystemItem>>> pathsSupplier;
	BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> filesRetriever;
	Predicate<FileSystemItem> refreshPathIf;
	Boolean fileFiltersExtenallySet;
	FileSystemItem.Criteria fileFilter;
	FileSystemItem.Criteria additionalFileFilters;
	ClassCriteria classCriteria;
	
	Supplier<Collection<FileSystemItem>> pathsRetriever;
	SearchContext<?> searchContext;
	
	boolean useDefaultPathScannerClassLoader;
	boolean useDefaultPathScannerClassLoaderAsParent;
	ClassLoader parentClassLoaderForPathScannerClassLoader;
	
	boolean waitForSearchEnding;
	boolean optimizePaths;
	
	SearchConfigAbst() {
		pathsSupplier = classLoader -> {
			return new AbstractMap.SimpleEntry<>(classLoader, ConcurrentHashMap.newKeySet());
		};
		useDefaultPathScannerClassLoader(true);
		waitForSearchEnding = true;
		classCriteria = ClassCriteria.create();
		filesRetriever = FIND_IN_ALL_CHILDREN;
	}
	
	<I, C extends SearchContext<I>> C init(ClassPathScanner.Abst<I, C, ?> classPathScanner) {
		if (fileFilter == null) {
			fileFiltersExtenallySet = additionalFileFilters != null;
			fileFilter = FileSystemItem.Criteria.forClassTypeFiles(
				classPathScanner.config.resolveStringValue(
					classPathScanner.getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties()
				)
			);
		} else {
			fileFiltersExtenallySet = Boolean.TRUE;
		}

		PathScannerClassLoader defaultPathScannerClassLoader = classPathScanner.getDefaultPathScannerClassLoader(this);
		if (useDefaultPathScannerClassLoaderAsParent) {
			parentClassLoaderForPathScannerClassLoader = defaultPathScannerClassLoader;
		}
		PathScannerClassLoader pathScannerClassLoader = useDefaultPathScannerClassLoader ?
			defaultPathScannerClassLoader :
			PathScannerClassLoader.create(
				parentClassLoaderForPathScannerClassLoader, 
				classPathScanner.pathHelper,
				getAllFileFilters()
					
			);
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
		pathsRetriever = () -> {
			Collection<FileSystemItem> pathsToBeScanned = pathsSupplier.apply(pathScannerClassLoader).getValue();
			if (pathsToBeScanned.isEmpty()) {
				pathsToBeScanned.addAll(classPathScanner.pathHelper.getPaths(ClassPathScanner.Configuration.Key.DEFAULT_SEARCH_CONFIG_PATHS)
					.stream().map(FileSystemItem::ofPath).collect(Collectors.toSet()));
			}
			if (optimizePaths && filesRetriever != FIND_IN_CHILDREN && !fileFiltersExtenallySet) {
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
	
	public S by(ClassCriteria classCriteria) {
		this.classCriteria = classCriteria;
		return (S)this;
	}
	
	@SafeVarargs
	public final S addPaths(Collection<String>... pathColls) {
		for (Collection<String> pathColl : pathColls) {
			pathsSupplier = pathsSupplier.andThen(classLoaderAndPaths -> {
				for (String absolutePath : pathColl) {
					classLoaderAndPaths.getValue().add(FileSystemItem.ofPath(absolutePath));
				}
				return classLoaderAndPaths;
			});
		}
		return (S)this;
	}
	
	@SafeVarargs
	public final S addFileSystemItems(Collection<FileSystemItem>... pathColls) {
		for (Collection<FileSystemItem> pathColl : pathColls) {
			pathsSupplier = pathsSupplier.andThen(classLoaderAndPaths -> {
				for (FileSystemItem absolutePath : pathColl) {
					classLoaderAndPaths.getValue().add(absolutePath);
				}
				return classLoaderAndPaths;
			});
		}
		return (S)this;
	}
	
	public S addPaths(String... paths) {
		return addPaths(Arrays.asList(paths));
	}
	
	@SafeVarargs
	public final S addResources(ClassLoader classLoader, Collection<String>... pathColls) {
		for (Collection<String> pathColl : pathColls) {
			pathsSupplier = pathsSupplier.andThen(classLoaderAndPaths -> {
				classLoaderAndPaths.getValue().addAll(
					ClassLoaders.getResources(
						classLoader != null? classLoader : classLoaderAndPaths.getKey(),
						pathColl
					)
				);
				return classLoaderAndPaths;
			});
		}
		return (S)this;
	}
	
	@SafeVarargs
	public final S addResources(ClassLoader classLoader, String... paths) {
		return addResources(classLoader, Arrays.asList(paths));
	}
	
	@SafeVarargs
	public final S addResources(String... paths) {
		return addResources(Arrays.asList(paths)); 
	}	
	
	@SafeVarargs
	public final S addResources(Collection<String>... pathCollections) {
		return addResources(null, pathCollections);
	}
	
	public S waitForSearchEnding(boolean waitForSearchEnding) {
		this.waitForSearchEnding = waitForSearchEnding;
		return (S)this;
	}
	
	public S checkForAddedClasses() {
		this.refreshPathIf = FileSystemItem -> false;
		return (S)this;
	}
	
	public S optimizePaths(boolean flag) {
		this.optimizePaths = flag;
		return (S)this;
	}
	
	
	public S setFileFilter(FileSystemItem.Criteria filter) {
		this.fileFilter = filter;
		return (S)this;
	}
	
	public S addFileFilter(FileSystemItem.Criteria filter) {
		if (additionalFileFilters == null) {
			additionalFileFilters = filter;
			return (S)this;
		}
		additionalFileFilters = additionalFileFilters.and(filter);
		return (S)this;
	}
	
	ClassCriteria getClassCriteria() {
		return this.classCriteria;
	}
	
	public S useDefaultPathScannerClassLoader(boolean value) {
		useDefaultPathScannerClassLoader = value;
		useDefaultPathScannerClassLoaderAsParent = !useDefaultPathScannerClassLoader;
		parentClassLoaderForPathScannerClassLoader = null;
		return (S)this;
	}
	
	public S useAsParentClassLoader(ClassLoader classLoader) {
		if (classLoader == null)  {
			Driver.throwException("Parent class loader could not be null");
		}
		useDefaultPathScannerClassLoader = false;
		useDefaultPathScannerClassLoaderAsParent = false;
		parentClassLoaderForPathScannerClassLoader = classLoader;
		return (S)this;
	}
	
	public S useDefaultPathScannerClassLoaderAsParent(boolean value) {
		useDefaultPathScannerClassLoaderAsParent = value;
		useDefaultPathScannerClassLoader = !useDefaultPathScannerClassLoaderAsParent;		
		parentClassLoaderForPathScannerClassLoader = null;
		return (S)this;
	}
	
	public S useNewIsolatedClassLoader() {
		useDefaultPathScannerClassLoaderAsParent = false;
		useDefaultPathScannerClassLoader = false;		
		parentClassLoaderForPathScannerClassLoader = null;
		return (S)this;
	}
	
	
	Collection<FileSystemItem> getPathsToBeScanned() {
		return pathsRetriever.get();
	}
	
	BiFunction<FileSystemItem, Criteria, Collection<FileSystemItem>> getFilesRetriever() {
		return filesRetriever;
	}
	
	boolean isDefaultFilesRetrieverSet() {
		return filesRetriever == FIND_IN_ALL_CHILDREN;
	}
	
	public S findInChildren() {
		filesRetriever = FIND_IN_CHILDREN;
		return (S)this;
	}
	
	public S findRecursiveInChildren() {
		filesRetriever = FIND_RECURSIVE_IN_CHILDREN;
		return (S)this;
	}
	
	public S findInAllChildren() {
		filesRetriever = FIND_IN_ALL_CHILDREN;
		return (S)this;
	}
	
	Predicate<FileSystemItem> getRefreshPathIf() {
		return this.refreshPathIf;
	}
	
	FileSystemItem.Criteria getAllFileFilters(){
		if (additionalFileFilters != null) {
			return fileFilter.and(additionalFileFilters);
		}
		return fileFilter;
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
	
	abstract S newInstance();
	
	@SuppressWarnings("hiding")
	public <S extends SearchConfigAbst<S>> S copyTo(S destConfig) {
		destConfig.classCriteria = this.classCriteria.createCopy();
		destConfig.pathsRetriever = this.pathsRetriever;
		destConfig.filesRetriever = this.filesRetriever;
		destConfig.refreshPathIf = this.refreshPathIf;
		destConfig.fileFilter = this.fileFilter;
		destConfig.additionalFileFilters = this.additionalFileFilters;
		destConfig.pathsSupplier = this.pathsSupplier;
		destConfig.optimizePaths = this.optimizePaths;
		destConfig.useDefaultPathScannerClassLoader = this.useDefaultPathScannerClassLoader;
		destConfig.parentClassLoaderForPathScannerClassLoader = this.parentClassLoaderForPathScannerClassLoader;
		destConfig.useDefaultPathScannerClassLoaderAsParent = this.useDefaultPathScannerClassLoaderAsParent;
		destConfig.waitForSearchEnding = this.waitForSearchEnding;
		return destConfig;
	}
	
	public S createCopy() {
		return copyTo(newInstance());
	}
	
	@Override
	public void close() {
		this.classCriteria.close();
		this.classCriteria = null;
		this.filesRetriever = null;
		this.pathsRetriever = null;
		this.refreshPathIf = null;
		this.fileFilter = null;
		this.pathsSupplier = null;
		this.additionalFileFilters = null;
		this.parentClassLoaderForPathScannerClassLoader = null;
	}
}
