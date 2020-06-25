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

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.io.FileSystemItem;

@SuppressWarnings("unchecked")
abstract class SearchConfigAbst<S extends SearchConfigAbst<S>> implements AutoCloseable, ManagedLogger {
	
	ClassCriteria classCriteria;
	Collection<String> paths;
	ClassLoader parentClassLoaderForMainClassLoader;
	FileSystemItem.Criteria scanFileCriteria;
	boolean optimizePaths;
	boolean useSharedClassLoaderAsMain;
	boolean deleteFoundItemsOnClose;
	boolean useSharedClassLoaderAsParent;
	boolean waitForSearchEnding;
	boolean checkForAddedClasses;
	

	SearchConfigAbst(Collection<String>... pathsColl) {
		useSharedClassLoaderAsMain(true);
		deleteFoundItemsOnClose = true;
		waitForSearchEnding = true;
		paths = new HashSet<>();
		addPaths(pathsColl);
		classCriteria = ClassCriteria.create();
		scanFileCriteria = FileSystemItem.Criteria.create();
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
	
	Collection<String> getPaths() {
		return paths;
	}
	
	public S by(ClassCriteria classCriteria) {
		this.classCriteria = classCriteria;
		return (S)this;
	}
	
	public S withScanFileCriteria(FileSystemItem.Criteria scanFileCriteria) {
		this.scanFileCriteria = scanFileCriteria;
		return (S)this;
	}
	
	FileSystemItem.Criteria getScanFileCriteria(){
		return this.scanFileCriteria;
	}
	
	ClassCriteria getClassCriteria() {
		return classCriteria;
	}
	
	public S deleteFoundItemsOnClose(boolean flag) {
		this.deleteFoundItemsOnClose = flag;
		return (S)this;
	}	

	public S useSharedClassLoaderAsMain(boolean value) {
		useSharedClassLoaderAsMain = value;
		useSharedClassLoaderAsParent = !useSharedClassLoaderAsMain;
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S useAsParentClassLoader(ClassLoader classLoader) {
		if (classLoader == null)  {
			throw Throwables.toRuntimeException("Parent class loader could not be null");
		}
		useSharedClassLoaderAsMain = false;
		useSharedClassLoaderAsParent = false;
		parentClassLoaderForMainClassLoader = classLoader;
		return (S)this;
	}
	
	public S useSharedClassLoaderAsParent(boolean value) {
		useSharedClassLoaderAsParent = value;
		useSharedClassLoaderAsMain = !useSharedClassLoaderAsParent;		
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S isolateClassLoader() {
		useSharedClassLoaderAsParent = false;
		useSharedClassLoaderAsMain = false;		
		parentClassLoaderForMainClassLoader = null;
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
		this.checkForAddedClasses = true;
		return (S)this;
	}
	
	boolean isCheckForAddedClassesEnabled() {
		return this.checkForAddedClasses;
	}
	
	abstract S newInstance();
	
	public S copyTo(S destConfig) {
		destConfig.classCriteria = this.classCriteria.createCopy();
		destConfig.paths = new HashSet<>();
		destConfig.paths.addAll(this.paths);
		destConfig.scanFileCriteria = this.scanFileCriteria.createCopy();
		destConfig.optimizePaths = this.optimizePaths;
		destConfig.useSharedClassLoaderAsMain = this.useSharedClassLoaderAsMain;
		destConfig.parentClassLoaderForMainClassLoader = this.parentClassLoaderForMainClassLoader;
		destConfig.useSharedClassLoaderAsParent = this.useSharedClassLoaderAsParent;
		destConfig.deleteFoundItemsOnClose = this.deleteFoundItemsOnClose;
		destConfig.waitForSearchEnding = this.waitForSearchEnding;
		destConfig.checkForAddedClasses = this.checkForAddedClasses;
		return destConfig;
	}
	
	public S createCopy() {
		return copyTo(newInstance());
	}
	
	@Override
	public void close() {
		this.classCriteria.close();
		this.classCriteria = null;
		this.paths.clear();
		this.paths = null;
		this.parentClassLoaderForMainClassLoader = null;
	}

}
