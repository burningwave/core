package org.burningwave.core.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.burningwave.core.common.Streams;


public class ByteBufferOutputStream extends OutputStream {

    private static final float REALLOCATION_FACTOR = 1.1f;

    private Integer initialCapacity;
    private Integer initialPosition;
    private ByteBuffer buffer;
    private Boolean closeable;
    
    public ByteBufferOutputStream() {
    	this(Streams.DEFAULT_BUFFER_SIZE);
    }
    
    public ByteBufferOutputStream(boolean closeable) {
    	this(Streams.DEFAULT_BUFFER_SIZE, closeable);
    }

    public ByteBufferOutputStream(ByteBuffer buffer, boolean closeable) {
        this.buffer = buffer;
        this.initialPosition = buffer.position();
        this.initialCapacity = buffer.capacity();
        this.closeable = closeable;
    }
    
    public ByteBufferOutputStream(int initialCapacity) {
        this(initialCapacity, true);
    }

    public ByteBufferOutputStream(int initialCapacity, boolean closeable) {
        this(Streams.DEFAULT_BYTE_BUFFER_ALLOCATION.apply(initialCapacity), closeable);
    }
    
    public void markAsCloseable(boolean closeable) {
    	this.closeable = closeable;
    }
    
    public void write(int b) {
        ensureRemaining(1);
        buffer.put((byte) b);
    }

    public void write(byte[] bytes, int off, int len) {
        ensureRemaining(len);
        buffer.put(bytes, off, len);
    }

    public void write(ByteBuffer sourceBuffer) {
        ensureRemaining(sourceBuffer.remaining());
        buffer.put(sourceBuffer);
    }

    public int position() {
        return buffer.position();
    }

    public int remaining() {
        return buffer.remaining();
    }

    public int limit() {
        return buffer.limit();
    }

    public void position(int position) {
        ensureRemaining(position - buffer.position());
        buffer.position(position);
    }

    public int initialCapacity() {
        return initialCapacity;
    }

    public void ensureRemaining(int remainingBytesRequired) {
        if (remainingBytesRequired > buffer.remaining())
            expandBuffer(remainingBytesRequired);
    }

    private void expandBuffer(int remainingRequired) {
        int expandSize = Math.max((int) (buffer.limit() * REALLOCATION_FACTOR), buffer.position() + remainingRequired);
        ByteBuffer temp = Streams.DEFAULT_BYTE_BUFFER_ALLOCATION.apply(expandSize);
        int limit = limit();
        buffer.flip();
        temp.put(buffer);
        buffer.limit(limit);
        buffer.position(initialPosition);
        buffer = temp;
    }
    
    
    protected InputStream toBufferedInputStream() {
        return new ByteBufferInputStream(buffer);
    }
    
    @Override
    public void close() {
    	if (closeable) {
    		this.initialCapacity = null;
    		this.initialPosition = null;
    		this.buffer = null;
    		this.closeable = null;
    	}
    }

	public ByteBuffer getBuffer() {
		return Streams.shareContent(buffer);
	}

	public byte[] toByteArray() {
		return Streams.toByteArray(getBuffer());
	}
}