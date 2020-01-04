package org.burningwave.core.classes;

import org.burningwave.core.classes.ClassHelper.ClassLoaderDelegate;

public class ForJDKVersionLaterThan8 extends ClassLoaderDelegate {

	@Override
	public Package getPackage(ClassLoader classLoader, String packageName) {
		return classLoader.getDefinedPackage(packageName);
	}	
	
}