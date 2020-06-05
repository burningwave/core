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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.function.Supplier;

import org.burningwave.core.io.FileSystemItem.CheckFile;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.PathHelper.ComparePathsResult;


public class PathScannerClassLoader extends org.burningwave.core.classes.MemoryClassLoader {
	Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	Collection<String> loadedPaths;
	private ByteCodeHunter byteCodeHunter;
	private PathHelper pathHelper;
	private CheckFile byteCodeHunterSearchConfigCheckFileOptions;
	
	static {
        ClassLoader.registerAsParallelCapable();
    }
	
	protected PathScannerClassLoader(
		ClassLoader parentClassLoader,
		PathHelper pathHelper,
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		CheckFile byteCodeHunterSearchConfigCheckFileOptions
	) {
		super(parentClassLoader);
		this.pathHelper = pathHelper;
		this.byteCodeHunterSupplier = byteCodeHunterSupplier;
		loadedPaths = new HashSet<>();
		this.byteCodeHunterSearchConfigCheckFileOptions = byteCodeHunterSearchConfigCheckFileOptions;
	}
	
	public static PathScannerClassLoader create(ClassLoader parentClassLoader, PathHelper pathHelper, Supplier<ByteCodeHunter> byteCodeHunterSupplier, CheckFile byteCodeHunterSearchConfigCheckFileOptions) {
		return new PathScannerClassLoader(parentClassLoader, pathHelper, byteCodeHunterSupplier, byteCodeHunterSearchConfigCheckFileOptions);
	}
	
	ByteCodeHunter getByteCodeHunter() {
		return byteCodeHunter != null ?
			byteCodeHunter :
			(byteCodeHunter = byteCodeHunterSupplier.get());	
	}
	
	public void scanPathsAndAddAllByteCodesFound(Collection<String> paths, boolean considerURLClassLoaderPathsAsLoadedPaths) {
		ComparePathsResult checkPathsResult = compareWithAllLoadedPaths(paths, considerURLClassLoaderPathsAsLoadedPaths);
		if (!checkPathsResult.getNotContainedPaths().isEmpty()) {
			synchronized (loadedPaths) {
				checkPathsResult = compareWithAllLoadedPaths(paths, considerURLClassLoaderPathsAsLoadedPaths);
				if (!checkPathsResult.getNotContainedPaths().isEmpty()) {
					try(ByteCodeHunter.SearchResult result = getByteCodeHunter().findBy(
						SearchConfig.forPaths(
							checkPathsResult.getNotContainedPaths()
						).considerURLClassLoaderPathsAsScanned(
							considerURLClassLoaderPathsAsLoadedPaths
						).checkFileOptions(
							byteCodeHunterSearchConfigCheckFileOptions
						).optimizePaths(
							true
						)
					)) {
						if (checkPathsResult.getPartialContainedDirectories().isEmpty() && checkPathsResult.getPartialContainedFiles().isEmpty()) {
							for (Entry<String, JavaClass> entry : result.getClassesFlatMap().entrySet()) {
								JavaClass javaClass = entry.getValue();
								addByteCode(javaClass.getName(), javaClass.getByteCode());
							}
						} else {
							for (Entry<String, JavaClass> entry : result.getClassesFlatMap().entrySet()) {
								if (check(checkPathsResult, entry.getKey())) {
									JavaClass javaClass = entry.getValue();
									addByteCode(javaClass.getName(), javaClass.getByteCode());
								}
							}
						}
					};
					loadedPaths.addAll(checkPathsResult.getNotContainedPaths());
				}
			}
		}
	}

	private boolean check(ComparePathsResult checkPathsResult, String key) {
		for (Collection<String> filePaths : checkPathsResult.getPartialContainedFiles().values()) {
			for (String filePath : filePaths) {
				if (key.startsWith(Paths.clean(filePath))) {
					return false;
				}
			}
		}
		for (Collection<String> filePaths : checkPathsResult.getPartialContainedDirectories().values()) {
			for (String diretctoyPath : filePaths) {
				if (key.startsWith(Paths.clean(diretctoyPath) + "/")) {
					return false;
				}
			}
		}
		return true;
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
		byteCodeHunterSupplier = null;
		loadedPaths.clear();
		loadedPaths = null;
		byteCodeHunter = null;
		pathHelper = null;
		super.close();
	}
}