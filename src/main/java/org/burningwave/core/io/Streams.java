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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;


public interface Streams {

	public static Streams create() {
		return new StreamsImpl();
	}

	public boolean isArchive(File file) throws IOException;

	public boolean isJModArchive(File file) throws IOException;

	public boolean isClass(File file) throws IOException;

	public boolean isArchive(ByteBuffer bytes);

	public boolean isJModArchive(ByteBuffer bytes);

	public boolean isClass(ByteBuffer bytes);

	public boolean is(File file, Predicate<Integer> predicate) throws IOException;

	public byte[] toByteArray(InputStream inputStream);

	public ByteBuffer toByteBuffer(InputStream inputStream, int size);

	public ByteBuffer toByteBuffer(InputStream inputStream);
	
	public <K, V> Map<K, V> toPropertiesMap(Supplier<InputStream> inputStreamSupplier);
	
	public void feelPropertiesMap(Supplier<InputStream> inputStreamSupplier, Map<?, ?> map);
	
	public<K,V> Map<K, V> toPropertiesMap(InputStream inputStream);
	
	public void feelPropertiesMap(InputStream inputStream, Map<?, ?> map);

	public StringBuffer getAsStringBuffer(InputStream inputStream);

	public void copy(InputStream input, OutputStream output);

	public FileSystemItem store(String fileAbsolutePath, byte[] bytes);

	public FileSystemItem store(String fileAbsolutePath, ByteBuffer bytes);

}
