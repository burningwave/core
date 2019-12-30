package org.burningwave;

public class RuntimeException extends java.lang.RuntimeException {
	
	private static final long serialVersionUID = 7526382733941265525L;
	
	public RuntimeException(String message) {
		super(message);
	}

	public RuntimeException(Throwable exc) {
		super(exc);
	}
	
	public RuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
