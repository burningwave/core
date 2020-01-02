package org.burningwave.core.classes;

import org.burningwave.core.classes.ClassLoaderDelegate;

public class ClassLoaderDelegate4JDKVersionLaterThan8 extends ClassLoaderDelegate {

	@Override
	public Package getPackage(ClassLoader classLoader, String packageName) {
		return classLoader.getDefinedPackage(packageName);
	}	
	
}