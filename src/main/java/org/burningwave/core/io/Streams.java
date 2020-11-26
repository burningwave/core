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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.burningwave.core.iterable.Properties;


public interface Streams {
	public static class Configuration {
		
		public static class Key {
		
			static final String BYTE_BUFFER_SIZE = "streams.default-buffer-size";
			static final String BYTE_BUFFER_ALLOCATION_MODE = "streams.default-byte-buffer-allocation-mode";
		
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			defaultValues.put(Key.BYTE_BUFFER_SIZE, "1024");
			defaultValues.put(
				Key.BYTE_BUFFER_ALLOCATION_MODE,
				"ByteBuffer::allocateDirect"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	
	public static Streams create(Properties config) {
		return new StreamsImpl(config);
	}
	
	public boolean isArchive(File file) throws IOException;

	public boolean isJModArchive(File file) throws IOException;

	public boolean isClass(File file) throws IOException;

	public boolean isArchive(ByteBuffer bytes);

	public boolean isJModArchive(ByteBuffer bytes);

	public boolean isClass(ByteBuffer bytes);

	public boolean is(File file, Predicate<Integer> predicate) throws IOException;

	public byte[] toByteArray(InputStream inputStream);

	public ByteBuffer toByteBuffer(InputStream inputStream);

	public StringBuffer getAsStringBuffer(InputStream inputStream);

	public long copy(InputStream input, OutputStream output);

	public byte[] toByteArray(ByteBuffer byteBuffer);

	public ByteBuffer shareContent(ByteBuffer byteBuffer);

	public FileSystemItem store(String fileAbsolutePath, byte[] bytes);

	public FileSystemItem store(String fileAbsolutePath, ByteBuffer bytes);

}
