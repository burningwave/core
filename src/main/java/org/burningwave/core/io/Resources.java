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
package org.burningwave.core.io;

import static org.burningwave.core.Throwables.throwException;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class Resources {
	private final static Map.Entry<URL, InputStream> EMPTY_RESOURCE;

	static {
		EMPTY_RESOURCE = new AbstractMap.SimpleImmutableEntry<>(null, null);
	}

	public  Collection<URL> getAll(String resourceRelativePath, ClassLoader resourceClassLoader, boolean onlyParents) {
		return Driver.getResources(
			resourceRelativePath,
			false,
			onlyParents ?
				ClassLoaders.getAllParents(resourceClassLoader) :
				ClassLoaders.getHierarchy(resourceClassLoader)
		);
	}

	public Collection<URL> getAll(String resourceRelativePath, ClassLoader... resourceClassLoaders) {
		return Driver.getResources(resourceRelativePath, false, resourceClassLoaders);
	}

	public URL get(String resourceRelativePath, ClassLoader resourceClassLoader, boolean onlyParents) {
		Collection<URL> resourceURLs = Driver.getResources(resourceRelativePath, true, onlyParents ?
			ClassLoaders.getAllParents(resourceClassLoader) :
			ClassLoaders.getHierarchy(resourceClassLoader)
		);
		if (!resourceURLs.isEmpty()) {
			return  resourceURLs.iterator().next();
		}
		return null;
	}

	public URL get(String resourceRelativePath, ClassLoader... resourceClassLoaders) {
		Collection<URL> resourceURLs = Driver.getResources(resourceRelativePath, true, resourceClassLoaders);
		if (!resourceURLs.isEmpty()) {
			return  resourceURLs.iterator().next();
		}
		return null;
	}

	public URL get(String resourceRelativePath, Collection<ClassLoader> resourceClassLoaders) {
		Collection<URL> resourceURLs = Driver.getResources(resourceRelativePath, true, resourceClassLoaders);
		if (!resourceURLs.isEmpty()) {
			return  resourceURLs.iterator().next();
		}
		return null;
	}

	public Map<URL, InputStream> getAsInputStreams(String resourceRelativePath, ClassLoader resourceClassLoader, boolean onlyParents) {
		return getAsInputStreams(
			resourceRelativePath, () ->
			Driver.getResources(
				resourceRelativePath,
				false,
				onlyParents ?
					ClassLoaders.getAllParents(resourceClassLoader) :
					ClassLoaders.getHierarchy(resourceClassLoader)
			)
		);
	}

	public Map<URL, InputStream> getAsInputStreams(String resourceRelativePath, ClassLoader... resourceClassLoaders) {
		return getAsInputStreams(
			resourceRelativePath,
			() -> Driver.getResources(resourceRelativePath, false, resourceClassLoaders)
		);
	}

	private Map<URL, InputStream> getAsInputStreams(String resourceRelativePath, Supplier<Collection<URL>> resourceSupplier) {
		Map<URL, InputStream> streams = new LinkedHashMap<>();
		for (URL resourceURL : resourceSupplier.get() ) {
			try {
				streams.put(
					resourceURL,
					resourceURL.openStream()
				);
			} catch (Throwable exc) {
				try {
					streams.put(
						resourceURL,
						FileSystemItem.of(resourceURL).toInputStream()
					);
				} catch (Throwable exc2) {
					return throwException(exc);
				}
			}
		}
		return streams;
	}

	public Map.Entry<URL, InputStream> getAsInputStream(String resourceRelativePath, ClassLoader resourceClassLoader, boolean onlyParents) {
		return getAsInputStream(
			resourceRelativePath,() ->
			Driver.getResources(resourceRelativePath, true, onlyParents ?
				ClassLoaders.getAllParents(resourceClassLoader) :
				ClassLoaders.getHierarchy(resourceClassLoader)
			)
		);
	}

	public Map.Entry<URL, InputStream> getAsInputStream(String resourceRelativePath, ClassLoader... resourceClassLoaders) {
		return getAsInputStream(
			resourceRelativePath, () ->
				Driver.getResources(resourceRelativePath, true, resourceClassLoaders)
		);
	}

	private Map.Entry<URL, InputStream> getAsInputStream(String resourceRelativePath, Supplier<Collection<URL>> resourceSupplier) {
		Collection<URL> resourceURLs = resourceSupplier.get();
		if (!resourceURLs.isEmpty()) {
			URL resourceURL = resourceURLs.iterator().next();
			try {
				return new AbstractMap.SimpleImmutableEntry<>(
					resourceURL,
					resourceURL.openStream()
				);
			} catch (Throwable exc) {
				try {
					return new AbstractMap.SimpleImmutableEntry<>(
						resourceURL,
						FileSystemItem.of(resourceURL).toInputStream()
					);
				} catch (Throwable exc2) {
					return throwException(exc);
				}
			}
		}
		return EMPTY_RESOURCE;
	}


	public FileSystemItem get(Class<?> cls) {
		return FileSystemItem.of(get(Classes.toPath(cls), Classes.getClassLoader(cls)));
	}

	public FileSystemItem getClassPath(Class<?> cls) {
		String classRelativePath = Classes.toPath(cls);
		String classAbsolutePath = FileSystemItem.of(get(classRelativePath, Classes.getClassLoader(cls))).getAbsolutePath();
		return FileSystemItem.ofPath(classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf(classRelativePath) - 1) );
	}


	public StringBuffer getAsStringBuffer(String resourceRelativePath, ClassLoader resourceClassLoader, boolean onlyParents) throws IOException {
		try (InputStream inputSteram = getAsInputStream(resourceRelativePath, resourceClassLoader, onlyParents).getValue()) {
			return getAsStringBuffer(inputSteram);
		}
	}


	public StringBuffer getAsStringBuffer(InputStream inputStream) throws IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
					inputStream
				)
			)
		) {
			StringBuffer result = new StringBuffer();
			String sCurrentLine;
			while ((sCurrentLine = reader.readLine()) != null) {
				result.append(sCurrentLine + "\n");
			}
			return result;
		}
	}

	@SafeVarargs
	public final Collection<FileSystemItem> getAsFileSystemItems(ClassLoader classLoader, String... paths) {
		return getAsFileSystemItems(classLoader, Arrays.asList(paths));
	}

	@SafeVarargs
	public final Collection<FileSystemItem> getAsFileSystemItems(ClassLoader classLoader, Collection<String>... pathCollections) {
		Collection<FileSystemItem> paths = new HashSet<>();
		for (Collection<String> pathCollection : pathCollections) {
			for (String path : pathCollection) {
				paths.addAll(
					getAll(path, classLoader, false).stream().map(url ->
						FileSystemItem.of(url)
					).collect(Collectors.toSet())
				);
			}
		}
		return paths;
	}
}
