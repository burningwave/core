package org.burningwave.core.service;

public interface ServiceInterface {
	
	public default void printMyName() {
		System.out.println("My name is" + this.getClass().getName());
	}
	
}
