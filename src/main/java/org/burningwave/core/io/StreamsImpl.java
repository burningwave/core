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

import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.function.Predicate;

import org.burningwave.core.Identifiable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.Executor;
import org.burningwave.core.iterable.Properties;

class StreamsImpl implements Streams, Identifiable, Properties.Listener, ManagedLogger {

	String instanceId;

	StreamsImpl() {
		instanceId = getId();
	}

	@Override
	public boolean isArchive(File file) throws IOException {
		return is(file, this::isArchive);
	}

	@Override
	public boolean isJModArchive(File file) throws IOException {
		return is(file, this::isJModArchive);
	}

	@Override
	public boolean isClass(File file) throws IOException {
		return is(file, this::isClass);
	}

	@Override
	public boolean isArchive(ByteBuffer bytes) {
		return is(bytes, this::isArchive);
	}

	@Override
	public boolean isJModArchive(ByteBuffer bytes) {
		return is(bytes, this::isJModArchive);
	}

	@Override
	public boolean isClass(ByteBuffer bytes) {
		return is(bytes, this::isClass);
	}

	@Override
	public boolean is(File file, Predicate<Integer> predicate) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")){
			return raf.length() > 4 && predicate.test(raf.readInt());
	    }
	}

	private boolean is(ByteBuffer bytes, Predicate<Integer> predicate) {
		return bytes.capacity() > 4 && bytes.limit() > 4 && predicate.test(BufferHandler.duplicate(bytes).getInt());
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

	@Override
	public byte[] toByteArray(InputStream inputStream) {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			copy(inputStream, outputStream);
			return outputStream.toByteArray();
		} catch (Throwable exc) {
			return Driver.throwException(exc);
		}
	}

	@Override
	public ByteBuffer toByteBuffer(InputStream inputStream, int streamSize) {
		/*try (ByteBufferOutputStream outputStream = ByteBufferHandler.newByteBufferOutputStream(streamSize)) {
			copy(inputStream, outputStream);
			return outputStream.toByteBuffer();
		}*/
		try {
			byte[] heapBuffer = BufferHandler.newByteArrayWithDefaultSize();
			int bytesRead;
			ByteBuffer byteBuffer = BufferHandler.newByteBuffer(streamSize);
			while (-1 != (bytesRead = inputStream.read(heapBuffer))) {
				byteBuffer = BufferHandler.put(byteBuffer, heapBuffer, bytesRead);
			}
			return BufferHandler.shareContent(byteBuffer);
		} catch (Throwable exc) {
			return Driver.throwException(exc);
		}
	}

	@Override
	public ByteBuffer toByteBuffer(InputStream inputStream) {
		return toByteBuffer(inputStream, -1);
	}

	@Override
	public StringBuffer getAsStringBuffer(InputStream inputStream) {
		return Executor.get(() -> {
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
		});
	}

	@Override
	public void copy(InputStream input, OutputStream output) {
		Executor.run(() -> {
			byte[] buffer = BufferHandler.newByteArrayWithDefaultSize();
			int bytesRead = 0;
			while (-1 != (bytesRead = input.read(buffer))) {
				output.write(buffer, 0, bytesRead);
			}
		});
	}

	@Override
	public FileSystemItem store(String fileAbsolutePath, byte[] bytes) {
		return store(fileAbsolutePath, BufferHandler.allocate(bytes.length).put(bytes, 0, bytes.length));
	}

	@Override
	public FileSystemItem store(String fileAbsolutePath, ByteBuffer bytes) {
		ByteBuffer content = BufferHandler.shareContent(bytes);
		File file = new File(fileAbsolutePath);
		Synchronizer.execute(fileAbsolutePath, () -> {
			if (!file.exists()) {
				new File(file.getParent()).mkdirs();
			} else {
				file.delete();
			}
			Executor.run(() -> {
				try(ByteBufferInputStream inputStream = new ByteBufferInputStream(content); FileOutputStream fileOutputStream = FileOutputStream.create(file, true)) {
					copy(inputStream, fileOutputStream);
				}
			});
		});
		return FileSystemItem.ofPath(file.getAbsolutePath());
	}
}