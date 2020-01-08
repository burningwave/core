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
package org.burningwave.core.classes.hunter;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.common.Strings;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.PathHelper.CheckResult;


public class PathMemoryClassLoader extends org.burningwave.core.classes.MemoryClassLoader {
	Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	Collection<String> loadedPaths;
	private ByteCodeHunter byteCodeHunter;
	private PathHelper pathHelper;
	
	protected PathMemoryClassLoader(
		ClassLoader parentClassLoader,
		PathHelper pathHelper,
		ClassHelper classHelper,
		Supplier<ByteCodeHunter> byteCodeHunterSupplier
	) {
		super(parentClassLoader, classHelper);
		this.pathHelper = pathHelper;
		this.byteCodeHunterSupplier = byteCodeHunterSupplier;
		loadedPaths = ConcurrentHashMap.newKeySet();
	}
	
	public static PathMemoryClassLoader create(ClassLoader parentClassLoader, PathHelper pathHelper, ClassHelper classHelper, Supplier<ByteCodeHunter> byteCodeHunterSupplier) {
		return new PathMemoryClassLoader(parentClassLoader, pathHelper, classHelper, byteCodeHunterSupplier);
	}
	
	ByteCodeHunter getByteCodeHunter() {
		return byteCodeHunter != null ?
			byteCodeHunter :
			(byteCodeHunter = byteCodeHunterSupplier.get());	
	}
	
	void scanPathsAndLoadAllFoundClasses(Collection<String> paths, boolean considerURLClassLoaderPathsAsLoadedPaths, int maxParallelTasksForUnit) {
		CheckResult checkPathsResult = checkPaths(paths, considerURLClassLoaderPathsAsLoadedPaths);
		if (!checkPathsResult.getNotContainedPaths().isEmpty()) {
			synchronized (loadedPaths) {
				checkPathsResult = checkPaths(paths, considerURLClassLoaderPathsAsLoadedPaths);
				if (!checkPathsResult.getNotContainedPaths().isEmpty()) {
					try(SearchResult<String, JavaClass> result = getByteCodeHunter().findBy(
						SearchConfig.forPaths(
							checkPathsResult.getNotContainedPaths()
						).useSharedClassLoaderAsMain(
							true
						).considerURLClassLoaderPathsAsScanned(
							considerURLClassLoaderPathsAsLoadedPaths
						).maxParallelTasksForUnit(maxParallelTasksForUnit)
					)) {
						if (checkPathsResult.getPartialContainedDirectories().isEmpty() && checkPathsResult.getPartialContainedFiles().isEmpty()) {
							for (Entry<String, JavaClass> entry : result.getItemsFoundFlatMap().entrySet()) {
								JavaClass javaClass = entry.getValue();
								addCompiledClass(javaClass.getName(), javaClass.getByteCode());
							}
						} else {
							for (Entry<String, JavaClass> entry : result.getItemsFoundFlatMap().entrySet()) {
								if (check(checkPathsResult, entry.getKey())) {
									JavaClass javaClass = entry.getValue();
									addCompiledClass(javaClass.getName(), javaClass.getByteCode());
								}
							}
						}
					};
					loadedPaths.addAll(checkPathsResult.getNotContainedPaths());
				}
			}
		}
	}

	private boolean check(CheckResult checkPathsResult, String key) {
		for (Collection<String> filePaths : checkPathsResult.getPartialContainedFiles().values()) {
			for (String filePath : filePaths) {
				if (key.startsWith(Strings.Paths.clean(filePath))) {
					return false;
				}
			}
		}
		for (Collection<String> filePaths : checkPathsResult.getPartialContainedDirectories().values()) {
			for (String diretctoyPath : filePaths) {
				if (key.startsWith(Strings.Paths.clean(diretctoyPath) + "/")) {
					return false;
				}
			}
		}
		return true;
	}

	CheckResult checkPaths(Collection<String> paths, boolean considerURLClassLoaderPathsAsLoadedPaths) {
		return pathHelper.check(getAllLoadedPaths(considerURLClassLoaderPathsAsLoadedPaths), paths);
	}
	
	@SuppressWarnings("resource")
	private Collection<String> getAllLoadedPaths(boolean considerURLClassLoaderPathsAsLoadedPaths) {
		Collection<String> allLoadedPaths = new LinkedHashSet<>(loadedPaths);
		ClassLoader classLoader = this;
		while((classLoader = classLoader.getParent()) != null) {
			if (classLoader instanceof PathMemoryClassLoader) {
				allLoadedPaths.addAll(((PathMemoryClassLoader)classLoader).loadedPaths);
			} else if (considerURLClassLoaderPathsAsLoadedPaths && classLoader instanceof URLClassLoader) {
				URL[] resUrl = ((URLClassLoader)classLoader).getURLs();
				for (int i = 0; i < resUrl.length; i++) {
					allLoadedPaths.add(Strings.Paths.clean(resUrl[i].getFile()));
				}
			}
		}
		return allLoadedPaths;
	}
	
	@Override
	public void close() {
		byteCodeHunterSupplier = null;
		loadedPaths.clear();
		loadedPaths = null;
		byteCodeHunter = null;
		pathHelper = null;
		super.close();
	}
}