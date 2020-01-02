package org.burningwave.core.classes;

public abstract class ClassLoaderDelegate {
	
	public abstract Package getPackage(ClassLoader classLoader, String packageName);
	
}
