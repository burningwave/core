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

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.concurrent.Mutex;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.PathHelper.ComparePathsResult;


public class PathScannerClassLoader extends org.burningwave.core.classes.MemoryClassLoader {
	Collection<String> allLoadedPaths;
	Collection<String> loadedPaths;
	PathHelper pathHelper;
	FileSystemItem.Criteria scanFileCriteriaAndConsumer;
	Mutex.Manager mutexManager;
	
	public static class Configuration {
		public static class Key {
			
			public final static String PARENT_CLASS_LOADER = "path-scanner-class-loader.parent";
			public final static String SEARCH_CONFIG_CHECK_FILE_OPTIONS = "path-scanner-class-loader.search-config.check-file-option";
			
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(Configuration.Key.PARENT_CLASS_LOADER + CodeExecutor.PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX,
				"${"+ Configuration.Key.PARENT_CLASS_LOADER + ".additional-imports}" +  ";" +
				ComponentSupplier.class.getName() + ";" +
				FileSystemItem.class.getName() + ";" + 
				PathScannerClassLoader.class.getName() + ";" +
				Supplier.class.getName() + ";"
			);
			DEFAULT_VALUES.put(Configuration.Key.PARENT_CLASS_LOADER + CodeExecutor.PROPERTIES_FILE_CODE_EXECUTOR_NAME_KEY_SUFFIX, PathScannerClassLoader.class.getPackage().getName() + ".PathScannerClassLoaderRetriever");
			//DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
		}
	}
	
	static {
        ClassLoader.registerAsParallelCapable();
    }
	
	PathScannerClassLoader(
		ClassLoader parentClassLoader,
		PathHelper pathHelper,
		FileSystemItem.Criteria scanFileCriteria
	) {
		super(parentClassLoader);
		this.pathHelper = pathHelper;
		this.allLoadedPaths = ConcurrentHashMap.newKeySet();
		this.loadedPaths = ConcurrentHashMap.newKeySet();
		this.mutexManager = Mutex.Manager.create(this);
		this.scanFileCriteriaAndConsumer = scanFileCriteria.createCopy();
	}
	
	public static PathScannerClassLoader create(ClassLoader parentClassLoader, PathHelper pathHelper) {
		return new PathScannerClassLoader(parentClassLoader, pathHelper, FileSystemItem.Criteria.forClassTypeFiles(FileSystemItem.CheckingOption.FOR_NAME));
	}
	
	public static PathScannerClassLoader create(ClassLoader parentClassLoader, PathHelper pathHelper, FileSystemItem.Criteria scanFileCriteria) {
		return new PathScannerClassLoader(parentClassLoader, pathHelper, scanFileCriteria);
	}
	
	public Collection<String> scanPathsAndAddAllByteCodesFound(Collection<String> paths) {
		return scanPathsAndAddAllByteCodesFound(paths, false);
	}
	
	public Collection<String> scanPathsAndAddAllByteCodesFound(Collection<String> paths, boolean checkForAddedClasses) {
		Collection<String> scannedPaths = new HashSet<>();
		if (!isClosed) {
			for (String path : paths) {
				if (!isClosed) {
					if (checkForAddedClasses || !hasBeenLoaded(path, !checkForAddedClasses)) {
						synchronized(mutexManager.getMutex(path)) {
							if (checkForAddedClasses || !hasBeenLoaded(path, !checkForAddedClasses)) {
								FileSystemItem pathFIS = FileSystemItem.ofPath(path);
								if (checkForAddedClasses) {
									pathFIS.refresh();
								}
								for (FileSystemItem child : pathFIS.getAllChildren()) {
									if (!isClosed) {
										if (scanFileCriteriaAndConsumer.testWithFalseResultForNullEntityOrTrueResultForNullPredicate(
											new FileSystemItem [] {child, pathFIS}
										)){
											try {
												JavaClass javaClass = JavaClass.create(child.toByteBuffer());
												addByteCode(javaClass.getName(), javaClass.getByteCode());
											} catch (Exception exc) {
												logError("Exception occurred while scanning " + child.getAbsolutePath(), exc);
											}
										}
									} else {
										break;
									}
								}
								if (!isClosed) {
									loadedPaths.add(path);
									allLoadedPaths.add(path);
								} else {
									break;
								}
								scannedPaths.add(path);
							}
						}
					}
				} else {
					break;
				}
			}
		}
		return scannedPaths;
	}
	
	@Override
	public InputStream getResourceAsStream(String name) {
		InputStream inputStream = super.getResourceAsStream(name);
		if (inputStream != null) {
			return inputStream;
		}
		AtomicReference<InputStream> inputStreamWrapper = new AtomicReference<>();
		FileSystemItem.Criteria scanFileCriteria = FileSystemItem.Criteria.forAllFileThat(child -> {
			if (child.isFile() && child.getAbsolutePath().endsWith(name)) {
				inputStreamWrapper.set(child.toInputStream());
				return true;
			}
			return false;
		});
		for (String loadedPath : loadedPaths) {
			FileSystemItem.ofPath(loadedPath).findFirstInAllChildren(scanFileCriteria);
			if (inputStreamWrapper.get() != null) {
				return inputStreamWrapper.get();
			}
		}
		return null;
	}
	
	public Map<String, InputStream> getResourcesAsStream(String name) {
		Map<String, InputStream> inputStreams = new HashMap<>();
		FileSystemItem.Criteria scanFileCriteria = FileSystemItem.Criteria.forAllFileThat(child -> {
			if (child.isFile() && child.getAbsolutePath().endsWith(name)) {
				inputStreams.put(child.getAbsolutePath(), child.toInputStream());
				return true;
			}
			return false;
		});
		for (String loadedPath : loadedPaths) {
			FileSystemItem.ofPath(loadedPath).findInAllChildren(scanFileCriteria);
		}
		return inputStreams;
	}
	
	public boolean hasBeenLoaded(String path, boolean considerURLClassLoaderPathsAsLoadedPaths) {
		if (allLoadedPaths.contains(path)) {
			return true;
		}
		FileSystemItem pathFIS = FileSystemItem.ofPath(path);
		for (String loadedPath : getAllLoadedPaths(considerURLClassLoaderPathsAsLoadedPaths)) {
			FileSystemItem loadedPathFIS = FileSystemItem.ofPath(loadedPath);
			if (pathFIS.isChildOf(loadedPathFIS) || pathFIS.equals(loadedPathFIS)) {
				allLoadedPaths.add(path);
				return true;
			}
		}
		return false;
	}
	
	ComparePathsResult compareWithAllLoadedPaths(Collection<String> paths, boolean considerURLClassLoaderPathsAsLoadedPaths) {
		return pathHelper.comparePaths(getAllLoadedPaths(considerURLClassLoaderPathsAsLoadedPaths), paths);
	}
	
	@SuppressWarnings("resource")
	private Collection<String> getAllLoadedPaths(boolean considerURLClassLoaderPathsAsLoadedPaths) {
		Collection<String> allLoadedPaths = new LinkedHashSet<>(loadedPaths);
		ClassLoader classLoader = this;
		while((classLoader = classLoader.getParent()) != null) {
			if (classLoader instanceof PathScannerClassLoader) {
				allLoadedPaths.addAll(((PathScannerClassLoader)classLoader).loadedPaths);
			} else if (considerURLClassLoaderPathsAsLoadedPaths && classLoader instanceof URLClassLoader) {
				URL[] resUrl = ((URLClassLoader)classLoader).getURLs();
				for (int i = 0; i < resUrl.length; i++) {
					allLoadedPaths.add(Paths.clean(resUrl[i].getFile()));
				}
			}
		}
		return allLoadedPaths;
	}
	
	@Override
	public void close() {
		Collection<String> loadedPaths = this.loadedPaths;
		if (loadedPaths != null) {
			loadedPaths.clear();
		}
		this.loadedPaths = null;
		Collection<String> allLoadedPaths = this.allLoadedPaths;
		if (allLoadedPaths != null) {
			allLoadedPaths.clear();
		}
		this.allLoadedPaths = null;
		Mutex.Manager mutexManager = this.mutexManager;
		if (mutexManager != null) {
			mutexManager.clear();
		}
		this.mutexManager = null;
		this.loadedPaths = null;
		pathHelper = null;
		super.close();
	}

}