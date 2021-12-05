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
package org.burningwave.core.jvm;

import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.burningwave.core.Component;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unchecked")
public class BufferHandler implements Component {

	public static class Configuration {

		public static class Key {

			static final String BUFFER_SIZE = "buffer-handler.default-buffer-size";
			static final String BUFFER_ALLOCATION_MODE = "buffer-handler.default-allocation-mode";

		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Key.BUFFER_SIZE, "1024");
			defaultValues.put(
				Key.BUFFER_ALLOCATION_MODE,
				"ByteBuffer::allocateDirect"
			);

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	Field directAllocatedByteBufferAddressField;
	int defaultBufferSize;
	Function<Integer, ByteBuffer> defaultByteBufferAllocator;
    final static float reallocationFactor = 1.1f;

	public BufferHandler(Map<?, ?> config) {
		init(config);
	}

	void init(Map<?, ?> config) {
		setDefaultByteBufferSize(config);
		setDefaultByteBufferAllocationMode(config);
		checkAndListenTo(config);
		Class<?> directByteBufferClass = ByteBuffer.allocateDirect(0).getClass();
		mainCycle:
		while (directByteBufferClass != null && directAllocatedByteBufferAddressField == null) {
			for (Field field : Driver.getDeclaredFields(directByteBufferClass)) {
				if (field.getName().equals("address")) {
					directAllocatedByteBufferAddressField = field;
					break mainCycle;
				}
			}
			directByteBufferClass = directByteBufferClass.getSuperclass();
		}
	}

	private void setDefaultByteBufferSize(Map<?, ?> config) {
		String defaultBufferSize = IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(Configuration.Key.BUFFER_SIZE)
			.on(config)
			.withDefaultValues(Configuration.DEFAULT_VALUES)
		);
		try {
			this.defaultBufferSize = Integer.valueOf(defaultBufferSize);
		} catch (Throwable exc) {
			String unit = defaultBufferSize.substring(defaultBufferSize.length()-2);
			String value = defaultBufferSize.substring(0, defaultBufferSize.length()-2);
			if (unit.equalsIgnoreCase("KB")) {
				this.defaultBufferSize = new BigDecimal(value).multiply(new BigDecimal(1024)).intValue();
			} else if (unit.equalsIgnoreCase("MB")) {
				this.defaultBufferSize = new BigDecimal(value).multiply(new BigDecimal(1024 * 1024)).intValue();
			} else {
				this.defaultBufferSize = Integer.valueOf(value);
			}
		}
		ManagedLoggerRepository.logInfo(getClass()::getName, "default buffer size: {} bytes", this.defaultBufferSize);
	}

	private void setDefaultByteBufferAllocationMode(Map<?, ?> config) {
		String defaultByteBufferAllocationMode = IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(Configuration.Key.BUFFER_ALLOCATION_MODE)
			.on(config)
			.withDefaultValues(Configuration.DEFAULT_VALUES)
		);
		if (defaultByteBufferAllocationMode.equalsIgnoreCase("ByteBuffer::allocate")) {
			this.defaultByteBufferAllocator = this::allocateInHeap;
			ManagedLoggerRepository.logInfo(getClass()::getName, "default allocation mode: ByteBuffer::allocate");
		} else {
			this.defaultByteBufferAllocator = this::allocateDirect;
			ManagedLoggerRepository.logInfo(getClass()::getName, "default allocation mode: ByteBuffer::allocateDirect");
		}
	}

	@Override
	public <K, V> void processChangeNotification(Properties config, Event event, K key, V newValue, V previousValue) {
		if (event.name().equals(Event.PUT.name())) {
			if (key instanceof String) {
				String keyAsString = (String)key;
				if (keyAsString.equals(Configuration.Key.BUFFER_SIZE)) {
					setDefaultByteBufferSize(config);
				} else if (keyAsString.equals(Configuration.Key.BUFFER_ALLOCATION_MODE)) {
					setDefaultByteBufferAllocationMode(config);
				}
			}
		}
	}

	public int getDefaultBufferSize() {
		return defaultBufferSize;
	}

	public static BufferHandler create(Map<?, ?> config) {
		return new BufferHandler(config);
	}

	public ByteBuffer allocate(int capacity) {
		return defaultByteBufferAllocator.apply(capacity);
	}

	public ByteBuffer allocateInHeap(int capacity) {
		return ByteBuffer.allocate(capacity);
	}

	public ByteBuffer allocateDirect(int capacity) {
		return ByteBuffer.allocateDirect(capacity);
	}

	public ByteBuffer duplicate(ByteBuffer buffer) {
		return buffer.duplicate();
	}

	public <T extends Buffer> int limit(T buffer) {
		return ((Buffer)buffer).limit();
	}

	public <T extends Buffer> int position(T buffer) {
		return ((Buffer)buffer).position();
	}

	public <T extends Buffer> T limit(T buffer, int newLimit) {
		return (T)((Buffer)buffer).limit(newLimit);
	}

	public <T extends Buffer> T position(T buffer, int newPosition) {
		return (T)((Buffer)buffer).position(newPosition);
	}

	public <T extends Buffer> T flip(T buffer) {
		return (T)((Buffer)buffer).flip();
	}

	public <T extends Buffer> int capacity(T buffer) {
		return ((Buffer)buffer).capacity();
	}

	public <T extends Buffer> int remaining(T buffer) {
		return ((Buffer)buffer).remaining();
	}

	public ByteBuffer put(ByteBuffer byteBuffer, byte[] heapBuffer) {
		return put(byteBuffer, heapBuffer, heapBuffer.length);
	}

	public ByteBuffer put(ByteBuffer byteBuffer, byte[] heapBuffer, int bytesToWrite) {
		return put(byteBuffer, heapBuffer, bytesToWrite, 0);
	}

	public ByteBuffer shareContent(ByteBuffer byteBuffer) {
		ByteBuffer duplicated = duplicate(byteBuffer);
		if (position(byteBuffer) > 0) {
			flip(duplicated);
		}
		return duplicated;
	}

	public ByteBuffer put(ByteBuffer byteBuffer, byte[] heapBuffer, int bytesToWrite, int initialPosition) {
		byteBuffer = ensureRemaining(byteBuffer, bytesToWrite, initialPosition);
		byteBuffer.put(heapBuffer, 0, bytesToWrite);
		return byteBuffer;
	}

	public byte[] toByteArray(ByteBuffer byteBuffer) {
    	byteBuffer = shareContent(byteBuffer);
    	byte[] result = new byte[limit(byteBuffer)];
    	byteBuffer.get(result, 0, result.length);
        return result;
	}

    public ByteBuffer ensureRemaining(ByteBuffer byteBuffer, int requiredBytes) {
        return ensureRemaining(byteBuffer, requiredBytes, 0);
    }

    public ByteBuffer ensureRemaining(ByteBuffer byteBuffer, int requiredBytes, int initialPosition) {
        if (requiredBytes > remaining(byteBuffer)) {
        	return expandBuffer(byteBuffer, requiredBytes, initialPosition);
        }
        return byteBuffer;
    }

	public ByteBuffer expandBuffer(ByteBuffer byteBuffer, int requiredBytes) {
		return expandBuffer(byteBuffer, requiredBytes, 0);
	}

	public ByteBuffer expandBuffer(ByteBuffer byteBuffer, int requiredBytes, int initialPosition) {
		int limit = limit(byteBuffer);
		ByteBuffer newBuffer = allocate(Math.max((int)(limit * reallocationFactor), position(byteBuffer) + requiredBytes));
		flip(byteBuffer);
		newBuffer.put(byteBuffer);
        limit(byteBuffer, limit);
        position(byteBuffer, initialPosition);
        return newBuffer;
	}

	public <T extends Buffer> long getAddress(T buffer) {
		try {
			return (long)Driver.getFieldValue(buffer, directAllocatedByteBufferAddressField);
		} catch (NullPointerException exc) {
			return (long)Driver.getFieldValue(buffer, getDirectAllocatedByteBufferAddressField());
		}
	}

	private Field getDirectAllocatedByteBufferAddressField() {
		if (directAllocatedByteBufferAddressField == null) {
			synchronized (this) {
				if (directAllocatedByteBufferAddressField == null) {
					try {
						this.wait();
					} catch (InterruptedException exc) {
						org.burningwave.core.Throwables.throwException(exc);
					}
				}
			}
		}
		return directAllocatedByteBufferAddressField;
	}

	public <T extends Buffer> boolean destroy(T buffer, boolean force) {
		if (buffer.isDirect()) {
			BufferHandler.Cleaner cleaner = getCleaner(buffer, force);
			if (cleaner != null) {
				return cleaner.clean();
			}
			return false;
		} else {
			return true;
		}
	}

	private <T extends Buffer> Object getInternalCleaner(T buffer, boolean findInAttachments) {
		if (buffer.isDirect()) {
			if (buffer != null) {
				Object cleaner;
				if ((cleaner = Fields.get(buffer, "cleaner")) != null) {
					return cleaner;
				} else if (findInAttachments){
					return getInternalCleaner(Fields.getDirect(buffer, "att"), findInAttachments);
				}
			}
		}
		return null;
	}

	private <T extends Buffer> Object getInternalDeallocator(T buffer, boolean findInAttachments) {
		if (buffer.isDirect()) {
			Object cleaner = getInternalCleaner(buffer, findInAttachments);
			if (cleaner != null) {
				return Fields.getDirect(cleaner, "thunk");
			}
		}
		return null;
	}

	private <T extends Buffer> Collection<T> getAllLinkedBuffers(T buffer) {
		Collection<T> allLinkedBuffers = new ArrayList<>();
		allLinkedBuffers.add(buffer);
		while((buffer = Fields.getDirect(buffer, "att")) != null) {
			allLinkedBuffers.add(buffer);
		}
		return allLinkedBuffers;
	}

	public ByteBuffer newByteBufferWithDefaultSize() {
		return allocate(defaultBufferSize);
	}

	public ByteBuffer newByteBuffer(int size) {
		return allocate(size > -1? size : defaultBufferSize);
	}

	public ByteBufferOutputStream newByteBufferOutputStreamWithDefaultSize() {
		return new ByteBufferOutputStream(defaultBufferSize);
	}

	public ByteBufferOutputStream newByteBufferOutputStream(int size) {
		return new ByteBufferOutputStream(size > -1? size : defaultBufferSize);
	}

	public byte[] newByteArrayWithDefaultSize() {
		return new byte[defaultBufferSize];
	}

	public byte[] newByteArray(int size) {
		return new byte[size > -1? size : defaultBufferSize];
	}

	public  <T extends Buffer> BufferHandler.Cleaner getCleaner(T buffer, boolean findInAttachments) {
		Object cleaner;
		if ((cleaner = getInternalCleaner(buffer, findInAttachments)) != null) {
			return new Cleaner () {

				@Override
				public boolean clean() {
					if (getAddress() != 0) {
						Methods.invokeDirect(cleaner, "clean");
						getAllLinkedBuffers(buffer).stream().forEach(linkedBuffer ->
							Fields.setDirect(linkedBuffer, "address", 0L)
						);
						return true;
					}
					return false;
				}

				long getAddress() {
					return Long.valueOf((long)Fields.getDirect(Fields.getDirect(cleaner, "thunk"), "address"));
				}

				@Override
				public boolean cleaningHasBeenPerformed() {
					return getAddress() == 0;
				}

			};
		}
		return null;
	}

	public <T extends Buffer> BufferHandler.Deallocator getDeallocator(T buffer, boolean findInAttachments) {
		if (buffer.isDirect()) {
			Object deallocator;
			if ((deallocator = getInternalDeallocator(buffer, findInAttachments)) != null) {
				return new Deallocator() {

					@Override
					public boolean freeMemory() {
						if (getAddress() != 0) {
							Methods.invokeDirect(deallocator, "run");
							getAllLinkedBuffers(buffer).stream().forEach(linkedBuffer ->
								Fields.setDirect(linkedBuffer, "address", 0L)
							);
							return true;
						} else {
							return false;
						}
					}

					public long getAddress() {
						return Long.valueOf((long)Fields.getDirect(deallocator, "address"));
					}

					@Override
					public boolean memoryHasBeenReleased() {
						return getAddress() == 0;
					}

				};
			}
		}
		return null;
	}

	public static interface Deallocator {

		public boolean freeMemory();

		boolean memoryHasBeenReleased();

	}

	public static interface Cleaner {

		public boolean clean();

		public boolean cleaningHasBeenPerformed();

	}

}