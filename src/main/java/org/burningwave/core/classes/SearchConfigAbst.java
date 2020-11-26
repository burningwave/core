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

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Closeable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.io.FileSystemItem;

@SuppressWarnings("unchecked")
abstract class SearchConfigAbst<S extends SearchConfigAbst<S>> implements Closeable, ManagedLogger {
	
	ClassCriteria classCriteria;
	Collection<String> paths;
	BiConsumer<ClassLoader, Collection<String>> resourceSupplier;
	ClassLoader parentClassLoaderForPathScannerClassLoader;
	Supplier<FileSystemItem.Criteria> defaultScanFileCriteriaSupplier;
	Supplier<FileSystemItem.Criteria> scanFileCriteriaSupplier;
	boolean optimizePaths;
	boolean useDefaultPathScannerClassLoader;
	boolean useDefaultPathScannerClassLoaderAsParent;
	boolean waitForSearchEnding;
	protected Predicate<String> checkForAddedClassesForAllPathThat;
	protected FileSystemItem.Criteria scanFileCriteriaModifier;
	

	SearchConfigAbst(Collection<String>... pathsColl) {
		useDefaultPathScannerClassLoader(true);
		waitForSearchEnding = true;
		paths = new HashSet<>();
		addPaths(pathsColl);
		classCriteria = ClassCriteria.create();
		checkForAddedClassesForAllPathThat = (path) -> false;
	}
	
	void init(PathScannerClassLoader classSupplier) {
		classCriteria.init(classSupplier);
	}
	
	@SafeVarargs
	public final S addPaths(Collection<String>... pathColls) {
		for (Collection<String> paths : pathColls) {
			this.paths.addAll(paths);
		}
		return (S)this;
	}
	
	public S addPaths(String... paths) {
		return addPaths(Arrays.asList(paths));
	}
	
	@SafeVarargs
	public final S addResources(ClassLoader classLoader, Collection<String>... pathColls) {
		if (classLoader == null) {
			if (resourceSupplier == null) {
				resourceSupplier = (cl, paths) -> {
					paths.addAll((ClassLoaders.getResources(cl, pathColls).stream().map(file -> file.getAbsolutePath()).collect(Collectors.toSet())));
				};
			} else {
				resourceSupplier = resourceSupplier.andThen((cl, paths) -> {
					paths.addAll((ClassLoaders.getResources(cl, pathColls).stream().map(file -> file.getAbsolutePath()).collect(Collectors.toSet())));
				});
			}
			return (S)this;
		}
		return addPaths(ClassLoaders.getResources(classLoader, pathColls).stream().map(file -> file.getAbsolutePath()).collect(Collectors.toSet()));
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
	
	Collection<String> getPaths() {
		return paths;
	}
	
	public S by(ClassCriteria classCriteria) {
		this.classCriteria = classCriteria;
		return (S)this;
	}
	
	@SafeVarargs
	public final S excludePathsThatMatch(Collection<String>... pathRegExCollections) {
		for(Collection<String> pathRegExCollection : pathRegExCollections) {
			for(String pathRegEx : pathRegExCollection) {
				if (scanFileCriteriaModifier == null) {
					scanFileCriteriaModifier = FileSystemItem.Criteria.create().excludePathsThatMatch(pathRegEx);
				} else {
					scanFileCriteriaModifier.and().excludePathsThatMatch(pathRegEx);
				}
			}
		}
		return (S)this;
	}
	
	@SafeVarargs
	public final S excludePathsThatMatch(String... regex) {
		return excludePathsThatMatch(Arrays.asList(regex));
	}
	
	public S notRecursiveOnPath(String path, boolean isAbsolute) {
		if (scanFileCriteriaModifier == null) {
			scanFileCriteriaModifier = FileSystemItem.Criteria.create().notRecursiveOnPath(path, isAbsolute);
		} else {
			scanFileCriteriaModifier.and().notRecursiveOnPath(path, isAbsolute);
		}
		return (S)this;
	}
	
	S withDefaultScanFileCriteria(FileSystemItem.Criteria scanFileCriteria) {
		defaultScanFileCriteriaSupplier = () -> scanFileCriteria;
		return (S)this;
	}
	
	public S withScanFileCriteria(FileSystemItem.Criteria scanFileCriteria) {
		this.scanFileCriteriaSupplier = () -> scanFileCriteria;
		return (S)this;
	}
	
	FileSystemItem.Criteria buildScanFileCriteria(){
		FileSystemItem.Criteria criteria = 
			scanFileCriteriaSupplier == null ?
				defaultScanFileCriteriaSupplier.get() :
				scanFileCriteriaSupplier.get();
			
		if (scanFileCriteriaModifier != null) {
			criteria = criteria.and(scanFileCriteriaModifier);
		}
		return criteria;
	}
	
	boolean scanFileCriteriaHasNoPredicate() {
		return scanFileCriteriaSupplier == null && scanFileCriteriaModifier == null;
	}
	
	ClassCriteria getClassCriteria() {
		return classCriteria;
	}
	
	BiConsumer<ClassLoader, Collection<String>> getResourceSupllier() {
		return this.resourceSupplier;
	}
	
	public S useDefaultPathScannerClassLoader(boolean value) {
		useDefaultPathScannerClassLoader = value;
		useDefaultPathScannerClassLoaderAsParent = !useDefaultPathScannerClassLoader;
		parentClassLoaderForPathScannerClassLoader = null;
		return (S)this;
	}
	
	public S useAsParentClassLoader(ClassLoader classLoader) {
		if (classLoader == null)  {
			Throwables.throwException("Parent class loader could not be null");
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
	
	public S waitForSearchEnding(boolean waitForSearchEnding) {
		this.waitForSearchEnding = waitForSearchEnding;
		return (S)this;
	}
	
	public S optimizePaths(boolean flag) {
		this.optimizePaths = flag;
		return (S)this;
	}
	
	public S checkForAddedClasses() {
		return checkForAddedClassesForAllPathThat(path ->
			this.paths.contains(path)
		);
	}

	public S checkForAddedClassesForAllPathThat(Predicate<String> refreshCacheFor) {
		this.checkForAddedClassesForAllPathThat = refreshCacheFor;
		return (S)this;
	}
	
	Predicate<String> getCheckForAddedClassesPredicate() {
		return checkForAddedClassesForAllPathThat;
	}
	
	abstract S newInstance();
	
	@SuppressWarnings("hiding")
	public <S extends SearchConfigAbst<S>> S copyTo(S destConfig) {
		destConfig.classCriteria = this.classCriteria.createCopy();
		destConfig.paths = new HashSet<>();
		destConfig.paths.addAll(this.paths);
		destConfig.resourceSupplier = this.resourceSupplier;
		destConfig.scanFileCriteriaSupplier = this.scanFileCriteriaSupplier;
		destConfig.defaultScanFileCriteriaSupplier = this.defaultScanFileCriteriaSupplier;
		if (this.scanFileCriteriaModifier != null) {
			destConfig.scanFileCriteriaModifier = this.scanFileCriteriaModifier.createCopy();
		}
		destConfig.optimizePaths = this.optimizePaths;
		destConfig.useDefaultPathScannerClassLoader = this.useDefaultPathScannerClassLoader;
		destConfig.parentClassLoaderForPathScannerClassLoader = this.parentClassLoaderForPathScannerClassLoader;
		destConfig.useDefaultPathScannerClassLoaderAsParent = this.useDefaultPathScannerClassLoaderAsParent;
		destConfig.waitForSearchEnding = this.waitForSearchEnding;
		destConfig.checkForAddedClassesForAllPathThat = this.checkForAddedClassesForAllPathThat;
		return destConfig;
	}
	
	public S createCopy() {
		return copyTo(newInstance());
	}
	
	@Override
	public void close() {
		this.classCriteria.close();
		this.classCriteria = null;
		this.scanFileCriteriaSupplier = null;
		this.defaultScanFileCriteriaSupplier = null;
		this.resourceSupplier = null;
		if (this.scanFileCriteriaModifier != null) {
			this.scanFileCriteriaModifier.close();
			this.scanFileCriteriaModifier = null;
		}
		this.paths.clear();
		this.paths = null;
		this.parentClassLoaderForPathScannerClassLoader = null;
	}

}
