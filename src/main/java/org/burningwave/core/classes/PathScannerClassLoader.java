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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.burningwave.core.concurrent.Mutex;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.PathHelper.ComparePathsResult;


public class PathScannerClassLoader extends org.burningwave.core.classes.MemoryClassLoader {
	Collection<String> loadedPaths;
	private PathHelper pathHelper;
	private FileSystemItem.Criteria scanFileCriteriaAndConsumer;
	private Mutex.Manager mutexManager;
	
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
		this.loadedPaths = ConcurrentHashMap.newKeySet();
		this.mutexManager = Mutex.Manager.create(this);
		this.scanFileCriteriaAndConsumer = scanFileCriteria.createCopy();
	}
	
	public static PathScannerClassLoader create(ClassLoader parentClassLoader, PathHelper pathHelper, FileSystemItem.Criteria scanFileCriteria) {
		return new PathScannerClassLoader(parentClassLoader, pathHelper, scanFileCriteria);
	}
	
	public void scanPathsAndAddAllByteCodesFound(Collection<String> paths, boolean considerURLClassLoaderPathsAsLoadedPaths) {
		scanPathsAndAddAllByteCodesFound(paths, considerURLClassLoaderPathsAsLoadedPaths, false);
	}
	
	public void scanPathsAndAddAllByteCodesFound(Collection<String> paths, boolean considerURLClassLoaderPathsAsLoadedPaths, boolean refreshCache) {
		if (!isClosed) {
			for (String path : paths) {
				if (!isClosed) {
					if (!hasBeenLoaded(path, considerURLClassLoaderPathsAsLoadedPaths)) {
						synchronized(mutexManager.getMutex(path)) {
							if (!hasBeenLoaded(path, considerURLClassLoaderPathsAsLoadedPaths)) {
								FileSystemItem pathFIS = FileSystemItem.ofPath(path);
								if (refreshCache) {
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
								} else {
									break;
								}
							}
						}
					}
				} else {
					break;
				}
			}
		}
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
		FileSystemItem pathFIS = FileSystemItem.ofPath(path);
		for (String loadedPath : getAllLoadedPaths(considerURLClassLoaderPathsAsLoadedPaths)) {
			FileSystemItem loadedPathFIS = FileSystemItem.ofPath(loadedPath);
			if (pathFIS.isChildOf(loadedPathFIS) || pathFIS.equals(loadedPathFIS)) {
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
		pathHelper = null;
		super.close();
	}

}