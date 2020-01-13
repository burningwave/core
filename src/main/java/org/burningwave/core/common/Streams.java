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
package org.burningwave.core.common;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.function.Function;

import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.BufferSize;
import org.burningwave.core.io.ByteBufferOutputStream;

public class Streams {
	//TODO: Dare la possibilià di configurare questi parametri
	public static int DEFAULT_BUFFER_SIZE = (int)BufferSize.KILO_BYTE.getValue();
	public static Function<Integer, ByteBuffer> DEFAULT_BYTE_BUFFER_ALLOCATION = ByteBuffer::allocateDirect;
	
	public static boolean isArchive(File file) throws FileNotFoundException, IOException {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")){
	    	return isArchive(raf.readInt());
	    } catch (EOFException exc) {
	    	//LoggersRepository.logError(Streams.class, "Exception occurred while calling isArchive on file " + file.getName(), exc);
	    	return false;
		}
	}
	
	public static boolean isArchive(ByteBuffer bytes) {
		bytes = bytes.duplicate();
		return isArchive(bytes.getInt());
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
    	byte[] result = new byte[((Buffer)byteBuffer).limit()];
    	byteBuffer.get(result, 0, result.length);
        return result;
    }

	public static ByteBuffer shareContent(ByteBuffer byteBuffer) {
		ByteBuffer duplicated = byteBuffer.duplicate();
		if (byteBuffer.position() > 0) {
			((Buffer)duplicated).flip();
		}		
		return duplicated;
	}
}
