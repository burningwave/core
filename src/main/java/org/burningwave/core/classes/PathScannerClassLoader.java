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
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.burningwave.core.concurrent.QueuedTaskExecutor.Task;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;


public class PathScannerClassLoader extends org.burningwave.core.classes.MemoryClassLoader {
	Map<String, Boolean> loadedPaths;
	PathHelper pathHelper;
	FileSystemItem.Criteria fileFilterAndProcessor;
	BiFunction<Throwable, FileSystemItem[], Boolean> exceptionHandler;

	public static abstract class Configuration {
		public static abstract class Key {

			public final static String PARENT_CLASS_LOADER = "path-scanner-class-loader.parent";
			public final static String SEARCH_CONFIG_CHECK_FILE_OPTION = "path-scanner-class-loader.search-config.check-file-option";

		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues = new HashMap<>();
			defaultValues.put(Configuration.Key.PARENT_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + IterableObjectHelper.getDefaultValuesSeparator() +
				"${"+ Configuration.Key.PARENT_CLASS_LOADER + "." + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_KEY + ".additional-imports}" +  IterableObjectHelper.getDefaultValuesSeparator()
			);
			defaultValues.put(Configuration.Key.PARENT_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX, PathScannerClassLoader.class.getPackage().getName() + ".ParentClassLoaderRetrieverForPathScannerClassLoader");
			//DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			defaultValues.put(Key.PARENT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
			defaultValues.put(Key.SEARCH_CONFIG_CHECK_FILE_OPTION, FileSystemItem.CheckingOption.FOR_NAME.getLabel());

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	static {
        ClassLoader.registerAsParallelCapable();
    }

	protected PathScannerClassLoader(
		ClassLoader parentClassLoader,
		PathHelper pathHelper,
		FileSystemItem.Criteria fileFilter
	) {
		super(parentClassLoader);
		this.pathHelper = pathHelper;
		this.loadedPaths = new ConcurrentHashMap<>();
		this.exceptionHandler = (exc, childAndPath) -> {
			if (!isClosed) {
				ManagedLoggerRepository.logError(getClass()::getName, "Exception occurred while scanning {}", exc, childAndPath[0].getAbsolutePath());
			} else {
				throw new IllegalStateException(Strings.compile("Could not execute the operation: {} is closed", this));
			}
			return false;
		};
		if (fileFilter != null) {
			setFileFilter(fileFilter);
		}
	}

	void setFileFilter(FileSystemItem.Criteria scanFileCriteria) {
		this.fileFilterAndProcessor = scanFileCriteria.createCopy().and().allFileThat((child, pathFIS) -> {
			JavaClass javaClass = child.toJavaClass();
			addByteCode0(javaClass.getName(), javaClass.getByteCode());
			return true;
		}).setExceptionHandler(scanFileCriteria.getExceptionHandler() != null ? scanFileCriteria.getExceptionHandler() : exceptionHandler);
	}

	public static PathScannerClassLoader create(ClassLoader parentClassLoader, PathHelper pathHelper, FileSystemItem.Criteria scanFileCriteria) {
		return new PathScannerClassLoader(parentClassLoader, pathHelper, scanFileCriteria);
	}

	public PathScannerClassLoader refresh() {
		Collection<String> loadedPaths = this.loadedPaths.keySet();
		for (String loadedPath : loadedPaths) {
			FileSystemItem.ofPath(loadedPath).reset();
		}
		scanPathsAndAddAllByteCodesFound(loadedPaths);
		return this;
	}

	public Collection<String> scanPathsWithoutRefreshingAndAddAllByteCodesFound(Collection<String> paths) {
		return scanPathsAndAddAllByteCodesFound(paths, (path) -> false);
	}

	public Collection<String> scanPathsAndAddAllByteCodesFound(Collection<String> paths) {
		return scanPathsAndAddAllByteCodesFound(paths, (path) -> true);
	}

	public Collection<String> scanPathsAndAddAllByteCodesFound(Collection<String> paths, Predicate<String> checkForAddedClasses) {
		Collection<String> scannedPaths = new HashSet<>();
		try {
			for (String path : paths) {
				if (checkForAddedClasses.test(path) || !hasBeenCompletelyLoaded(path)) {
					Synchronizer.execute(instanceId + "_" + path, () -> {
						if (checkForAddedClasses.test(path) || !hasBeenCompletelyLoaded(path)) {
							FileSystemItem pathFIS = FileSystemItem.ofPath(path);
							if (checkForAddedClasses.test(path)) {
								pathFIS.refresh();
							}
							Predicate<FileSystemItem[]> classFilePredicateAndConsumer = fileFilterAndProcessor.getPredicateOrTruePredicateIfPredicateIsNull();
							for (FileSystemItem child : pathFIS.getAllChildren()) {
								classFilePredicateAndConsumer.test(
									new FileSystemItem [] {child, pathFIS}
								);
							}
							loadedPaths.put(path, Boolean.TRUE);
							scannedPaths.add(path);
						}
					});
				}
			}
		} catch (Throwable exc) {
			if (isClosed) {
				ManagedLoggerRepository.logWarn(getClass()::getName, "Could not execute scanPathsAndAddAllByteCodesFound because {} has been closed", this.toString());
			} else {
				throw exc;
			}
		}
		return scannedPaths;
	}


	public URL[] getURLs() {
		Collection<URL> urls = loadedPaths.keySet().stream().map(absolutePath -> FileSystemItem.ofPath(absolutePath).getURL()).collect(Collectors.toSet());
		return urls.toArray(new URL[urls.size()]);
	}

	@Override
	public URL getResource(String name) {
		URL url = Resources.get(name, this.allParents);
		if (url != null) {
			return url;
		}
		AtomicReference<URL> inputStreamWrapper = new AtomicReference<>();
		FileSystemItem.Criteria scanFileCriteria = FileSystemItem.Criteria.forAllFileThat(child -> {
			if (child.isFile() && child.getAbsolutePath().endsWith("/" + name)) {
				inputStreamWrapper.set(child.getURL());
				return true;
			}
			return false;
		}).setExceptionHandler(exceptionHandler);
		for (String loadedPath : loadedPaths.keySet()) {
			FileSystemItem.ofPath(loadedPath).findFirstInAllChildren(scanFileCriteria);
			if (inputStreamWrapper.get() != null) {
				return inputStreamWrapper.get();
			}
		}
		return null;
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		Collection<URL> resourcesFound = Resources.getAll(name, this.allParents);
		FileSystemItem.Criteria scanFileCriteria = FileSystemItem.Criteria.forAllFileThat(child -> {
			if (child.isFile() && child.getAbsolutePath().endsWith("/" + name)) {
				resourcesFound.add(child.getURL());
				return true;
			}
			return false;
		}).setExceptionHandler(exceptionHandler);
		for (String loadedPath : loadedPaths.keySet()) {
			FileSystemItem.ofPath(loadedPath).findInAllChildren(scanFileCriteria);
		}
		return Collections.enumeration(resourcesFound);
	}


	@Override
	public InputStream getResourceAsStream(String name) {
		InputStream inputStream = super.getResourceAsStream(name);
		if (inputStream != null) {
			return inputStream;
		}
		AtomicReference<InputStream> inputStreamWrapper = new AtomicReference<>();
		FileSystemItem.Criteria scanFileCriteria = FileSystemItem.Criteria.forAllFileThat(child -> {
			if (child.isFile() && child.getAbsolutePath().endsWith("/" + name)) {
				inputStreamWrapper.set(child.toInputStream());
				return true;
			}
			return false;
		}).setExceptionHandler(exceptionHandler);
		for (String loadedPath : loadedPaths.keySet()) {
			FileSystemItem.ofPath(loadedPath).findFirstInAllChildren(scanFileCriteria);
			if (inputStreamWrapper.get() != null) {
				return inputStreamWrapper.get();
			}
		}
		return null;
	}


	public boolean hasBeenCompletelyLoaded(String path) {
		Boolean hasBeenCompletelyLoaded = loadedPaths.get(path);
		if (hasBeenCompletelyLoaded != null && hasBeenCompletelyLoaded) {
			return true;
		}
		FileSystemItem pathFIS = FileSystemItem.ofPath(path);
		for (String loadedPath : ClassLoaders.getAllLoadedPaths(ClassLoaders.getParent(this))) {
			FileSystemItem loadedPathFIS = FileSystemItem.ofPath(loadedPath);
			try {
				if (pathFIS.isChildOf(loadedPathFIS) || pathFIS.equals(loadedPathFIS)) {
					return true;
				}
			} catch (NullPointerException exc) {
				if (loadedPathFIS.exists() && pathFIS.exists()) {
					throw exc;
				}
			}
		}
		return false;
	}

	@Override
	protected Task closeResources() {
		return closeResources(
			PathScannerClassLoader.class.getName() + "@" + System.identityHashCode(this),
			() ->
				this.loadedPaths == null,
			task -> {
				super.closeResources().waitForFinish();
				this.loadedPaths.clear();
				this.loadedPaths = null;
				pathHelper = null;
				fileFilterAndProcessor = null;
				if (this.getClass().equals(PathScannerClassLoader.class)) {
					ManagedLoggerRepository.logInfo(getClass()::getName, "ClassLoader {} successfully closed", this);
				}
			}
		);
	}

}