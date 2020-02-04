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

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.function.Function;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate;

public class Streams implements ManagedLogger {
	public static int DEFAULT_BUFFER_SIZE = (int)BufferSize.KILO_BYTE.getValue();
	public static Function<Integer, ByteBuffer> DEFAULT_BYTE_BUFFER_ALLOCATION_MODE = ByteBuffer::allocateDirect;
	
	static {
		try {
			String defaultBufferSize = (String)org.burningwave.core.iterable.Properties.getGlobalProperty("streams.default-buffer-size");
			String unit = defaultBufferSize.substring(defaultBufferSize.length()-2);
			String value = defaultBufferSize.substring(0, defaultBufferSize.length()-2);
			if (unit.equalsIgnoreCase("KB")) {
				DEFAULT_BUFFER_SIZE = new BigDecimal(value).multiply(new BigDecimal(BufferSize.KILO_BYTE.getValue())).intValue();
			} else if (unit.equalsIgnoreCase("MB")) {
				DEFAULT_BUFFER_SIZE = new BigDecimal(value).multiply(new BigDecimal(BufferSize.MEGA_BYTE.getValue())).intValue();
			} else {
				DEFAULT_BUFFER_SIZE = Integer.valueOf(value);
			}
		} catch (Throwable e) {
			DEFAULT_BUFFER_SIZE = (int)BufferSize.KILO_BYTE.getValue();
		}
		ManagedLogger.Repository.getInstance().logInfo(Streams.class, "default buffer size: {} bytes", DEFAULT_BUFFER_SIZE);
		try {
			String defaultByteBufferAllocationMode = (String)org.burningwave.core.iterable.Properties.getGlobalProperty("streams.default-byte-buffer-allocation-mode");
			if (defaultByteBufferAllocationMode.equalsIgnoreCase("ByteBuffer::allocate")) {
				DEFAULT_BYTE_BUFFER_ALLOCATION_MODE = ByteBuffer::allocate;
				ManagedLogger.Repository.getInstance().logInfo(Streams.class, "default allocation mode: ByteBuffer::allocate");
			} else {
				DEFAULT_BYTE_BUFFER_ALLOCATION_MODE = ByteBuffer::allocateDirect;
				ManagedLogger.Repository.getInstance().logInfo(Streams.class, "default allocation mode: ByteBuffer::allocateDirect");
			}
		} catch (Throwable exc) {
			DEFAULT_BYTE_BUFFER_ALLOCATION_MODE = ByteBuffer::allocateDirect;
			ManagedLogger.Repository.getInstance().logInfo(Streams.class, "default allocation mode: ByteBuffer::allocateDirect");
		}
	}
	
	public static boolean isArchive(File file) throws FileNotFoundException, IOException {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")){
	    	return isArchive(raf.readInt());
	    } catch (EOFException exc) {
	    	//ManagedLogger.Repository.logError(Streams.class, "Exception occurred while calling isArchive on file " + file.getName(), exc);
	    	return false;
		}
	}
	
	public static boolean isArchive(ByteBuffer bytes) {
		bytes = bytes.duplicate();
		try {
			return isArchive(bytes.getInt());
		} catch (BufferUnderflowException exc) {
			ManagedLogger.Repository.getInstance().logError(Streams.class, "Exception occurred while calling isArchive", exc);
			return false;
		}
	}
	
	private static boolean isArchive(int fileSignature) {
		return fileSignature == 0x504B0304 || fileSignature == 0x504B0506 || fileSignature == 0x504B0708;
	}

	public static byte[] toByteArray(InputStream inputStream) {
		try (ByteBufferOutputStream output = new ByteBufferOutputStream()) {
			copy(inputStream, output);
			return output.toByteArray();
		}
	}

	public static ByteBuffer toByteBuffer(InputStream inputStream) {
		try (ByteBufferOutputStream output = new ByteBufferOutputStream()) {
			copy(inputStream, output);
			return output.toByteBuffer();
		}
	}

	
	public static long copy(InputStream input, OutputStream output) {
		return ThrowingSupplier.get(() -> {
			byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
			long count = 0L;
			int n = 0;
			while (-1 != (n = input.read(buffer))) {
				output.write(buffer, 0, n);
				count += n;
			}
			return count;
		});
	}
	
	public static byte[] toByteArray(ByteBuffer byteBuffer) {
    	byteBuffer = shareContent(byteBuffer);
    	byte[] result = new byte[ByteBufferDelegate.limit(byteBuffer)];
    	byteBuffer.get(result, 0, result.length);
        return result;
    }

	public static ByteBuffer shareContent(ByteBuffer byteBuffer) {
		ByteBuffer duplicated = byteBuffer.duplicate();
		if (ByteBufferDelegate.position(byteBuffer) > 0) {
			ByteBufferDelegate.flip(duplicated);
		}		
		return duplicated;
	}
	
	public static FileSystemItem store(String fileAbsolutePath, ByteBuffer bytes) {
		ByteBuffer content = shareContent(bytes);
		File file = new File(fileAbsolutePath);
		if (!file.exists()) {
			new File(file.getParent()).mkdirs();
		} else {
			file.delete();
		}
		ThrowingRunnable.run(() -> {					
			try(ByteBufferInputStream inputStream = new ByteBufferInputStream(content); FileOutputStream fileOutputStream = FileOutputStream.create(file, true)) {
				Streams.copy(inputStream, fileOutputStream);
				//ManagedLogger.Repository.logDebug(this.getClass(), "Class " + getName() + " WRITTEN to "+ Strings.Paths.clean(fileClass.getAbsolutePath()));
			}
		});
		Cache.PATH_FOR_CONTENTS.getOrDefault(
			file.getAbsolutePath(), () ->
			content
		);		
		return FileSystemItem.ofPath(file.getAbsolutePath());
	}
}
