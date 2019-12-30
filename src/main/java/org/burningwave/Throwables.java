package org.burningwave;

public class Throwables {
	
	public static RuntimeException toRuntimeException(Object obj) {
		if (obj instanceof RuntimeException) {
			return (RuntimeException)obj;
		} else if (obj instanceof String) {
			throw new RuntimeException((String)obj);
		} else {	
			return new RuntimeException((Throwable)obj);
		}
	}
	
}
