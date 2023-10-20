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
 * Copyright (c) 2019-2023 Roberto Gentili
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;


public class ByteBufferOutputStream extends OutputStream {

    private Integer initialCapacity;
    private Integer initialPosition;
    private ByteBuffer buffer;

    public ByteBufferOutputStream(ByteBuffer buffer) {
        this.buffer = buffer;
        this.initialPosition = BufferHandler.position(buffer);
        this.initialCapacity = BufferHandler.capacity(buffer);
    }

    public ByteBufferOutputStream(int initialCapacity) {
        this(BufferHandler.allocate(initialCapacity));
    }

    @Override
	public void write(int b) {
    	buffer = BufferHandler.ensureRemaining(buffer, 1, initialPosition);
        buffer.put((byte) b);
    }

    @Override
	public void write(byte[] bytes, int off, int len) {
    	buffer = BufferHandler.ensureRemaining(buffer, len, initialPosition);
        buffer.put(bytes, off, len);
    }

    public void write(ByteBuffer sourceBuffer) {
    	buffer = BufferHandler.ensureRemaining(buffer, BufferHandler.remaining(sourceBuffer), initialPosition);
        buffer.put(sourceBuffer);
    }

    public int position() {
        return BufferHandler.position(buffer);
    }

    public int remaining() {
        return BufferHandler.remaining(buffer);
    }

    public int limit() {
        return BufferHandler.limit(buffer);
    }

    public void position(int position) {
    	buffer = BufferHandler.ensureRemaining(buffer, position - BufferHandler.position(buffer), initialPosition);
        BufferHandler.position(buffer, position);
    }

    public int initialCapacity() {
        return initialCapacity;
    }

    InputStream toBufferedInputStream() {
        return new ByteBufferInputStream(buffer);
    }

	public ByteBuffer toByteBuffer() {
		return BufferHandler.shareContent(buffer);
	}

	public byte[] toByteArray() {
		return BufferHandler.toByteArray(toByteBuffer());
	}

    @Override
    public void close() {
    	this.initialCapacity = null;
		this.initialPosition = null;
		this.buffer = null;
    }
}