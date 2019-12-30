package org.burningwave.core.io;


public class FileSystemItemNotFoundException extends org.burningwave.RuntimeException {

	private static final long serialVersionUID = 7265824022880218451L;
	
	public FileSystemItemNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public FileSystemItemNotFoundException(java.io.FileNotFoundException exc) {
		super(exc);
	}

	public FileSystemItemNotFoundException(String message) {
		super(message);
	}
}