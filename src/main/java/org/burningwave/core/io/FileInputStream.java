package org.burningwave.core.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

import org.burningwave.core.Component;
import org.burningwave.core.common.Streams;
import org.burningwave.core.function.ThrowingRunnable;

public class FileInputStream extends java.io.FileInputStream implements Component {
	
	private File file;


	private FileInputStream(File file) throws FileNotFoundException {
		super(file);
		this.file = file;
	}
	
	private FileInputStream(String name) throws FileNotFoundException {
		this(name != null ? new File(name) : null);
	}
	
	public static FileInputStream create(File file) {
		try {
			return new FileInputStream(file);
		} catch (java.io.FileNotFoundException exc) {
			throw new FileSystemItemNotFoundException(exc);
		}
	}
	
	public static FileInputStream create(String name) {
		try {
			return new FileInputStream(name);
		} catch (java.io.FileNotFoundException exc) {
			throw new FileSystemItemNotFoundException(exc);
		}
	}
	
	public File getFile() {
		return this.file;
	}
	
	@Override
	public void close() {
		ThrowingRunnable.run(() -> super.close());
	}

	public String getAbsolutePath() {
		return this.getFile().getAbsolutePath().replace("\\", "/");
	}
	
	public byte[] toByteArray() {
		return Streams.toByteArray(this);
	}

	public ByteBuffer toByteBuffer() {
		return Streams.toByteBuffer(this);
	}
}