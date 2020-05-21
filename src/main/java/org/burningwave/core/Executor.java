package org.burningwave.core;

public interface Executor {
	
    public <T> T execute(Object... parameters) throws Throwable;
	
}
