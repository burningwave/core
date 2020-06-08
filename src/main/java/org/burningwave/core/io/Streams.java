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

import static org.burningwave.core.assembler.StaticComponentContainer.ByteBufferDelegate;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.Mutex;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.Properties;


public class Streams implements Component {
	public static class Configuration {
		
		public static class Key {
		
			private static final String BUFFER_SIZE = "streams.default-buffer-size";
			private static final String BYTE_BUFFER_ALLOCATION_MODE = "streams.default-byte-buffer-allocation-mode";
		
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(Key.BUFFER_SIZE, String.valueOf(BufferSize.KILO_BYTE.getValue()));
			DEFAULT_VALUES.put(
				Key.BYTE_BUFFER_ALLOCATION_MODE,
				"ByteBuffer::allocateDirect"
			);
		}
	}
	
	public int defaultBufferSize;
	Function<Integer, ByteBuffer> defaultByteBufferAllocationMode;
	private Mutex.Manager mutexManager;
	
	private Streams(Properties properties) {
		String defaultBufferSize = IterableObjectHelper.get(properties, Configuration.Key.BUFFER_SIZE, Configuration.DEFAULT_VALUES);
		String unit = defaultBufferSize.substring(defaultBufferSize.length()-2);
		String value = defaultBufferSize.substring(0, defaultBufferSize.length()-2);
		if (unit.equalsIgnoreCase("KB")) {
			this.defaultBufferSize = new BigDecimal(value).multiply(new BigDecimal(BufferSize.KILO_BYTE.getValue())).intValue();
		} else if (unit.equalsIgnoreCase("MB")) {
			this.defaultBufferSize = new BigDecimal(value).multiply(new BigDecimal(BufferSize.MEGA_BYTE.getValue())).intValue();
		} else {
			this.defaultBufferSize = Integer.valueOf(value);
		};
		logInfo("default buffer size: {} bytes", defaultBufferSize);
		String defaultByteBufferAllocationMode = IterableObjectHelper.get(properties, Configuration.Key.BYTE_BUFFER_ALLOCATION_MODE, Configuration.DEFAULT_VALUES);
		if (defaultByteBufferAllocationMode.equalsIgnoreCase("ByteBuffer::allocate")) {
			this.defaultByteBufferAllocationMode = ByteBuffer::allocate;
			logInfo("default allocation mode: ByteBuffer::allocate");
		} else {
			this.defaultByteBufferAllocationMode = ByteBuffer::allocateDirect;
			logInfo("default allocation mode: ByteBuffer::allocateDirect");
		}
		this.mutexManager = Mutex.Manager.create(this);
	}
	
	public static Streams create(Properties properties) {
		return new Streams(properties);
	}
	
	public boolean isArchive(File file) throws IOException {
		return is(file, this::isArchive);
	}
	
	public boolean isJModArchive(File file) throws IOException {
		return is(file, this::isJModArchive);
	}
	
	public boolean isClass(File file) throws IOException {
		return is(file, this::isClass);
	}
	
	public boolean isArchive(ByteBuffer bytes) {
		return is(bytes, this::isArchive);
	}
	
	public boolean isJModArchive(ByteBuffer bytes) {
		return is(bytes, this::isJModArchive);
	}
	
	public boolean isClass(ByteBuffer bytes) {
		return is(bytes, this::isClass);
	}
	
	public boolean is(File file, Predicate<Integer> predicate) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")){
			return raf.length() > 4 && predicate.test(raf.readInt());
	    }
	}
	
	private boolean is(ByteBuffer bytes, Predicate<Integer> predicate) {
		return bytes.capacity() > 4 && bytes.limit() > 4 && predicate.test(bytes.duplicate().getInt());
	}
	
	private boolean isArchive(int fileSignature) {
		return fileSignature == 0x504B0304 || fileSignature == 0x504B0506 || fileSignature == 0x504B0708 || isJModArchive(fileSignature);
	}
	
	private boolean isJModArchive(int fileSignature) {
		return fileSignature == 0x4A4D0100 || fileSignature == 0x4A4D0000;
	}
	
	private boolean isClass(int fileSignature) {
		return fileSignature == 0xCAFEBABE;
	}

	public byte[] toByteArray(InputStream inputStream) {
		try (ByteBufferOutputStream output = new ByteBufferOutputStream()) {
			copy(inputStream, output);
			return output.toByteArray();
		}
	}

	public ByteBuffer toByteBuffer(InputStream inputStream) {
		try (ByteBufferOutputStream output = new ByteBufferOutputStream()) {
			copy(inputStream, output);
			return output.toByteBuffer();
		}
	}
	
	public long copy(InputStream input, OutputStream output) {
		return ThrowingSupplier.get(() -> {
			byte[] buffer = new byte[defaultBufferSize];
			long count = 0L;
			int n = 0;
			while (-1 != (n = input.read(buffer))) {
				output.write(buffer, 0, n);
				count += n;
			}
			return count;
		});
	}
	
	public byte[] toByteArray(ByteBuffer byteBuffer) {
    	byteBuffer = shareContent(byteBuffer);
    	byte[] result = new byte[ByteBufferDelegate.limit(byteBuffer)];
    	byteBuffer.get(result, 0, result.length);
        return result;
    }

	public ByteBuffer shareContent(ByteBuffer byteBuffer) {
		ByteBuffer duplicated = byteBuffer.duplicate();
		if (ByteBufferDelegate.position(byteBuffer) > 0) {
			ByteBufferDelegate.flip(duplicated);
		}		
		return duplicated;
	}
	
	public FileSystemItem store(String fileAbsolutePath, byte[] bytes) {
		return store(fileAbsolutePath, defaultByteBufferAllocationMode.apply(bytes.length).put(bytes, 0, bytes.length));
	}
	
	public FileSystemItem store(String fileAbsolutePath, ByteBuffer bytes) {
		ByteBuffer content = shareContent(bytes);
		File file = new File(fileAbsolutePath);
		synchronized (mutexManager.getMutex(fileAbsolutePath)) {
			if (!file.exists()) {
				new File(file.getParent()).mkdirs();
			} else {
				file.delete();
			}
			ThrowingRunnable.run(() -> {					
				try(ByteBufferInputStream inputStream = new ByteBufferInputStream(content); FileOutputStream fileOutputStream = FileOutputStream.create(file, true)) {
					copy(inputStream, fileOutputStream);
					//ManagedLogger.Repository.logDebug(this.getClass(), "Class " + getName() + " WRITTEN to "+ Strings.Paths.clean(fileClass.getAbsolutePath()));
				}
			});
			Cache.pathForContents.getOrUploadIfAbsent(
				file.getAbsolutePath(), () ->
				content
			);
		}
		return FileSystemItem.ofPath(file.getAbsolutePath());
	}
	
}
