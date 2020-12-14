package org.burningwave.core.io;

import static org.burningwave.core.assembler.StaticComponentContainer.ByteBufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Identifiable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.Executor;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

class StreamsImpl implements Streams, Identifiable, Properties.Listener, ManagedLogger {
	int defaultBufferSize;
	Function<Integer, ByteBuffer> defaultByteBufferAllocator;
	String instanceId;
	
	StreamsImpl(java.util.Properties config) {
		instanceId = getId();
		setDefaultByteBufferSize(config);
		setDefaultByteBufferAllocationMode(config);
		if (config instanceof Properties) {
			listenTo((Properties)config);
		}
	}

	private void setDefaultByteBufferSize(java.util.Properties config) {
		String defaultBufferSize = IterableObjectHelper.resolveStringValue(config, Configuration.Key.BYTE_BUFFER_SIZE, Configuration.DEFAULT_VALUES);
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
			};
		}
		ManagedLoggersRepository.logInfo(getClass()::getName, "default buffer size: {} bytes", this.defaultBufferSize);
	}
	
	private void setDefaultByteBufferAllocationMode(java.util.Properties config) {
		String defaultByteBufferAllocationMode = IterableObjectHelper.resolveStringValue(config, Configuration.Key.BYTE_BUFFER_ALLOCATION_MODE, Configuration.DEFAULT_VALUES);
		if (defaultByteBufferAllocationMode.equalsIgnoreCase("ByteBuffer::allocate")) {
			this.defaultByteBufferAllocator = ByteBufferHandler::allocate;
			ManagedLoggersRepository.logInfo(getClass()::getName, "default allocation mode: ByteBuffer::allocate");
		} else {
			this.defaultByteBufferAllocator = ByteBufferHandler::allocateDirect;
			ManagedLoggersRepository.logInfo(getClass()::getName, "default allocation mode: ByteBuffer::allocateDirect");
		}
	}
	
	@Override
	public <K, V> void processChangeNotification(Properties config, Event event, K key, V newValue, V previousValue) {
		if (event.name().equals(Event.PUT.name())) {
			if (key instanceof String) {
				String keyAsString = (String)key;
				if (keyAsString.equals(Configuration.Key.BYTE_BUFFER_SIZE)) {
					setDefaultByteBufferSize(config);
				} else if (keyAsString.equals(Configuration.Key.BYTE_BUFFER_ALLOCATION_MODE)) {
					setDefaultByteBufferAllocationMode(config);
				}
			}
		}
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
		return bytes.capacity() > 4 && bytes.limit() > 4 && predicate.test(ByteBufferHandler.duplicate(bytes).getInt());
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
		try (ByteBufferOutputStream output = new ByteBufferOutputStream()) {
			copy(inputStream, output);
			return output.toByteArray();
		}
	}

	@Override
	public ByteBuffer toByteBuffer(InputStream inputStream) {
		try (ByteBufferOutputStream output = new ByteBufferOutputStream()) {
			copy(inputStream, output);
			return output.toByteBuffer();
		}
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
	public long copy(InputStream input, OutputStream output) {
		return Executor.get(() -> {
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
	
	@Override
	public byte[] toByteArray(ByteBuffer byteBuffer) {
    	byteBuffer = shareContent(byteBuffer);
    	byte[] result = new byte[ByteBufferHandler.limit(byteBuffer)];
    	byteBuffer.get(result, 0, result.length);
        return result;
    }

	@Override
	public ByteBuffer shareContent(ByteBuffer byteBuffer) {
		ByteBuffer duplicated = ByteBufferHandler.duplicate(byteBuffer);
		if (ByteBufferHandler.position(byteBuffer) > 0) {
			ByteBufferHandler.flip(duplicated);
		}		
		return duplicated;
	}
	
	@Override
	public FileSystemItem store(String fileAbsolutePath, byte[] bytes) {
		return store(fileAbsolutePath, defaultByteBufferAllocator.apply(bytes.length).put(bytes, 0, bytes.length));
	}
	
	@Override
	public FileSystemItem store(String fileAbsolutePath, ByteBuffer bytes) {
		ByteBuffer content = shareContent(bytes);
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