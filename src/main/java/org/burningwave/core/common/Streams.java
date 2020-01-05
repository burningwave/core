package org.burningwave.core.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.function.Function;

import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.BufferSize;
import org.burningwave.core.io.ByteBufferOutputStream;

public class Streams {
	//TODO: Dare la possibilià di configurare questi parametri
	public static int DEFAULT_BUFFER_SIZE = (int)BufferSize.KILO_BYTE.getValue();
	public static Function<Integer, ByteBuffer> DEFAULT_BYTE_BUFFER_ALLOCATION = ByteBuffer::allocateDirect;
	
	public static boolean isArchive(File file) {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")){
	    	return isArchive(raf.readInt());
	    } catch (IOException exc) {
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
			return output.getBuffer();
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
    	byte[] result = new byte[byteBuffer.limit()];
    	byteBuffer.get(result, 0, result.length);
        return result;
    }

	public static ByteBuffer shareContent(ByteBuffer byteBuffer) {
		ByteBuffer duplicated = byteBuffer.duplicate();
		if (byteBuffer.position() > 0) {
			duplicated.flip();
		}		
		return duplicated;
	}
}
