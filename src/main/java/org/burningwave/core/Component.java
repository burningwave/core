package org.burningwave.core;

public interface Component extends AutoCloseable, Logger {
	
	@Override
	default public void close() {
			
	}
	
}
