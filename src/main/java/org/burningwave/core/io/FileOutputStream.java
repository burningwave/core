package org.burningwave.core.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

public class FileOutputStream extends java.io.FileOutputStream implements Serializable, Component{

	private static final long serialVersionUID = -5546948914644351678L;
	
	private File file;


	private FileOutputStream(File file) throws FileNotFoundException {
		this(file, false);
	}

	
	private FileOutputStream(File file, boolean append)
			throws FileNotFoundException {
		super(file, append);
		this.file = file;
	}
	
	public static FileOutputStream create(File file, boolean append) {
		return ThrowingSupplier.get(() -> new FileOutputStream(file, append));
	}
	
	public static FileOutputStream create(File file) {
		return ThrowingSupplier.get(() -> new FileOutputStream(file));
	}
	
	public FileOutputStream(String name, boolean append)
			throws FileNotFoundException {
		this(name != null ? new File(name) : null, append);
	}

	
	public FileOutputStream(String name) throws FileNotFoundException {
		this(name != null ? new File(name) : null, false);
	}
	
	@Override
	public void close() {
		ThrowingRunnable.run(() -> super.close());
	}
	
	public File getFile() {
		return this.file;
	}
}