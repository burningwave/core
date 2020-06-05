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

import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemItem.CheckFile;
import org.burningwave.core.io.PathHelper;

@SuppressWarnings("unchecked")
abstract class SearchConfigAbst<S extends SearchConfigAbst<S>> implements AutoCloseable, ManagedLogger {
	public static class Key {
		
		public final static String DEFAULT_CHECK_FILE_OPTIONS = "hunters.default-search-config.check-file-options";		
		public static final String DEFAULT_SEARCH_CONFIG_PATHS = PathHelper.Configuration.Key.PATHS_PREFIX + "hunters.default-search-config.paths";
					
	}
	
	public final static Map<String, Object> DEFAULT_VALUES;

	static {
		DEFAULT_VALUES = new LinkedHashMap<>();

		DEFAULT_VALUES.put(
			Key.DEFAULT_SEARCH_CONFIG_PATHS, 
			PathHelper.Configuration.Key.MAIN_CLASS_PATHS_PLACE_HOLDER + ";"
		);
		DEFAULT_VALUES.put(
			Key.DEFAULT_CHECK_FILE_OPTIONS,
			FileSystemItem.CheckFile.FOR_NAME.getLabel()
		);
	}
	
	private final static Predicate<FileSystemItem> getFileNameChecker() {
		return file -> {
			String name = file.getName();
			return name.endsWith(".class") && 
				!name.endsWith("module-info.class") &&
				!name.endsWith("package-info.class");
		};
	}
	
	private final static Predicate<FileSystemItem> getFileSignatureChecker() {
		return file -> ThrowingSupplier.get(() -> Streams.isClass(file.toByteBuffer()));
	}
	
	ClassCriteria classCriteria;
	Collection<String> paths;
	ClassLoader parentClassLoaderForMainClassLoader;
	CheckFile checkFileOptions;
	boolean optimizePaths;
	boolean useSharedClassLoaderAsMain;
	boolean deleteFoundItemsOnClose;
	boolean useSharedClassLoaderAsParent;
	boolean considerURLClassLoaderPathsAsScanned;
	boolean waitForSearchEnding;
	

	SearchConfigAbst(Collection<String>... pathsColl) {
		useSharedClassLoaderAsMain(true);
		deleteFoundItemsOnClose = true;
		waitForSearchEnding = true;
		paths = new HashSet<>();
		addPaths(pathsColl);
		classCriteria = ClassCriteria.create();
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
	
	CheckFile getCheckFileOptions() {
		return checkFileOptions;
	}
	
	final Predicate<FileSystemItem> parseCheckFileOptionsValue() {
		return parseCheckFileOptionsValue(
			Optional.ofNullable(checkFileOptions).map(checkFileOptions -> 
				checkFileOptions.getLabel()
			).orElseGet(() -> null),
			IterableObjectHelper.get(
				GlobalProperties, Key.DEFAULT_CHECK_FILE_OPTIONS, DEFAULT_VALUES
			)
		);
	}
	
	final Predicate<FileSystemItem> parseCheckFileOptionsValue(String label, String defaultLabel) {
		if (label != null) {
			CheckFile checkFileOption = CheckFile.forLabel(label);
			if (checkFileOption.equals(CheckFile.FOR_NAME_OR_SIGNATURE)) {
				return getFileNameChecker().or(getFileSignatureChecker());
			} else if (checkFileOption.equals(CheckFile.FOR_SIGNATURE_OR_NAME)) {
				return getFileSignatureChecker().or(getFileNameChecker());
			} else if (checkFileOption.equals(CheckFile.FOR_NAME_AND_SIGNATURE)) {
				return getFileNameChecker().and(getFileSignatureChecker());
			} else if (checkFileOption.equals(CheckFile.FOR_NAME)) {
				return getFileNameChecker();
			} else if (checkFileOption.equals(CheckFile.FOR_SIGNATURE)) {
				return getFileSignatureChecker();
			}
		}
		return parseCheckFileOptionsValue(defaultLabel, null);
	}
	
	public S by(ClassCriteria classCriteria) {
		this.classCriteria = classCriteria;
		return (S)this;
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

	public S considerURLClassLoaderPathsAsScanned(
		boolean value
	) {
		this.considerURLClassLoaderPathsAsScanned = value;
		return (S)this;
	}
	
	public S optimizePaths(boolean flag) {
		this.optimizePaths = flag;
		return (S)this;
	}
	
	public S checkFileOptions(FileSystemItem.CheckFile options) {
		this.checkFileOptions = options;
		return (S)this;
	}
	
	abstract S newInstance();
	
	public <T extends SearchConfigAbst<T>> T copyTo(T destConfig) {
		destConfig.classCriteria = this.classCriteria.createCopy();
		destConfig.paths = new HashSet<>();
		destConfig.paths.addAll(this.paths);
		destConfig.checkFileOptions = this.checkFileOptions;
		destConfig.optimizePaths = this.optimizePaths;
		destConfig.useSharedClassLoaderAsMain = this.useSharedClassLoaderAsMain;
		destConfig.parentClassLoaderForMainClassLoader = this.parentClassLoaderForMainClassLoader;
		destConfig.useSharedClassLoaderAsParent = this.useSharedClassLoaderAsParent;
		destConfig.deleteFoundItemsOnClose = this.deleteFoundItemsOnClose;
		destConfig.considerURLClassLoaderPathsAsScanned = this.considerURLClassLoaderPathsAsScanned;
		destConfig.waitForSearchEnding = this.waitForSearchEnding;
		return destConfig;
	}
	
	public S createCopy() {
		S copy = newInstance();
		copy.classCriteria = this.classCriteria.createCopy();
		copy.paths = new HashSet<>();
		copy.paths.addAll(this.paths);
		copy.checkFileOptions = this.checkFileOptions;
		copy.optimizePaths = this.optimizePaths;
		copy.useSharedClassLoaderAsMain = this.useSharedClassLoaderAsMain;
		copy.parentClassLoaderForMainClassLoader = this.parentClassLoaderForMainClassLoader;
		copy.useSharedClassLoaderAsParent = this.useSharedClassLoaderAsParent;
		copy.deleteFoundItemsOnClose = this.deleteFoundItemsOnClose;
		copy.considerURLClassLoaderPathsAsScanned = this.considerURLClassLoaderPathsAsScanned;
		copy.waitForSearchEnding = this.waitForSearchEnding;
		return copy;
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
