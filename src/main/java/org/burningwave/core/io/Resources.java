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


import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;


public class Resources {
	
	
	public Map<URL, InputStream> getAsInputStreams(String resourceRelativePath, ClassLoader... resourceClassLoaders) {
		return Driver.getResourcesAsInputStreams(resourceRelativePath, resourceClassLoaders);
	}
	
	public InputStream getFirstFoundAsInputStreams(String resourceRelativePath, ClassLoader... resourceClassLoaders) throws IOException {
		Map<URL, InputStream> inputStreams = getAsInputStreams(resourceRelativePath, resourceClassLoaders);
		if (!inputStreams.isEmpty()) {
			return inputStreams.values().iterator().next();
		}
		return null;
	}

	
	public FileSystemItem get(Class<?> cls) {
		return FileSystemItem.of(Classes.getClassLoader(cls).getResource(Classes.toPath(cls)));
	}
	
	public FileSystemItem getClassPath(Class<?> cls) {
		String classRelativePath = Classes.toPath(cls);
		String classAbsolutePath = FileSystemItem.of(Classes.getClassLoader(cls).getResource(classRelativePath)).getAbsolutePath();
		return FileSystemItem.ofPath(classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf(classRelativePath) - 1) );
	}
	
	
	public StringBuffer getFirstFoundAsStringBuffer(String resourceRelativePath, ClassLoader... resourceClassLoaders) throws IOException {
		return getAsStringBuffer(getFirstFoundAsInputStreams(resourceRelativePath, resourceClassLoaders));
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
	
}
