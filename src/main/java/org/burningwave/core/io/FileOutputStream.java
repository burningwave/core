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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;

import org.burningwave.core.Component;
import org.burningwave.core.function.Executor;

public class FileOutputStream extends java.io.FileOutputStream implements Serializable, Component{

	private static final long serialVersionUID = -5546948914644351678L;
	
	private File file;


	private FileOutputStream(File file) throws FileNotFoundException {
		this(file, false);
	}

	
	private FileOutputStream(File file, boolean append)
			throws FileNotFoundException {
		super(file, append);
		this.file = file;
	}
	
	public static FileOutputStream create(File file, boolean append) {
		return Executor.get(() -> new FileOutputStream(file, append));
	}
	
	public static FileOutputStream create(File file) {
		return Executor.get(() -> new FileOutputStream(file));
	}
	
	public static FileOutputStream create(String absolutePath, boolean append)
			throws FileNotFoundException {
		return new FileOutputStream(absolutePath != null ? new File(absolutePath) : null, append);
	}

	
	public static FileOutputStream create(String absolutePath) throws FileNotFoundException {
		return new FileOutputStream(absolutePath != null ? new File(absolutePath) : null, false);
	}
	
	@Override
	public void close() {
		Executor.run(() -> super.close());
		file = null;
	}
	
	public File getFile() {
		return this.file;
	}
}