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
package org.burningwave.core.io;

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

import org.burningwave.core.Component;
import org.burningwave.core.function.Executor;

public class FileInputStream extends java.io.FileInputStream implements Component {
	
	private File file;
	private String absolutePath;


	private FileInputStream(File file) throws FileNotFoundException {
		super(file);
		this.file = file;
		this.absolutePath = Paths.clean(file.getAbsolutePath());
	}
	
	private FileInputStream(String absolutePath) throws FileNotFoundException {
		this(absolutePath != null ? new File(absolutePath) : null);
	}
	
	public static FileInputStream create(File file) {
		try {
			return new FileInputStream(file);
		} catch (java.io.FileNotFoundException exc) {
			return Throwables.throwException(new FileSystemItemNotFoundException(exc));
		}
	}
	
	public static FileInputStream create(String absolutePath) {
		try {
			return new FileInputStream(absolutePath);
		} catch (java.io.FileNotFoundException exc) {
			return Throwables.throwException(new FileSystemItemNotFoundException(exc));
		}
	}
	
	public File getFile() {
		return this.file;
	}
	
	@Override
	public void close() {
		Executor.run(() -> super.close());
		file = null;
		absolutePath = null;
	}

	public String getAbsolutePath() {
		return this.absolutePath;
	}
	
	public byte[] toByteArray() {
		return Streams.toByteArray(this);
	}

	public ByteBuffer toByteBuffer() {
		return Cache.pathForContents.getOrUploadIfAbsent(
			file.getAbsolutePath(), () -> 
			Streams.toByteBuffer(this)
		);
	}
}