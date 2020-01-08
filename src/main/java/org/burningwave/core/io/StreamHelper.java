/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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
package org.burningwave.core.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

public class StreamHelper implements Component {
	private FileSystemHelper fileSystemHelper;
	
	private StreamHelper(FileSystemHelper fileSystemHelper) {
		this.fileSystemHelper = fileSystemHelper;	
	}
	
	public static StreamHelper create(FileSystemHelper fileSystemHelper) {
		return new StreamHelper(fileSystemHelper);
	}
	
	public Collection<InputStream> getResourcesAsStreams(String... resourcesRelativePaths) {
		return fileSystemHelper.getResources((coll, fileSystemItem) -> coll.add(fileSystemItem.toInputStream()), resourcesRelativePaths);
	}
	
	public InputStream getResourceAsStream(String resourceRelativePath) {
		return fileSystemHelper.getResource((coll, fileSystemItem) ->
			coll.add(fileSystemItem.toInputStream()), 
			resourceRelativePath
		);
	}
	
	public StringBuffer getResourceAsStringBuffer(String resourceRelativePath) {
		return ThrowingSupplier.get(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResourceAsStream(resourceRelativePath)))) {
				StringBuffer result = new StringBuffer();
				String sCurrentLine;
				while ((sCurrentLine = reader.readLine()) != null) {
					result.append(sCurrentLine + "\n");
				}
				return result;
			}
		});
	}
		
	
	public void close(Collection<InputStream> inputStreams) {
		for (InputStream inputStream : inputStreams) {
			ThrowingRunnable.run(() -> inputStream.close());
		}
	}
}
