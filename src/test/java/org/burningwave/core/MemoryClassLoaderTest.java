package org.burningwave.core;

import static org.junit.Assert.assertTrue;

import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.reflection.PropertyAccessor;
import org.junit.jupiter.api.Test;

public class MemoryClassLoaderTest extends BaseTest {
	
	protected MemoryClassLoader getMemoryClassLoader(ClassLoader parent) {
		return MemoryClassLoader.create(
				parent
		);
	}
	
	@Test
	public void loadClassTestOne() {
		testNotNull(() ->
			getMemoryClassLoader(null).loadOrUploadClass(PropertyAccessor.class)
		);
	}
	
	@Test
	public void getResourceAsStreamTestOne() throws ClassNotFoundException {
		MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);
		memoryClassLoader.loadOrUploadClass(PropertyAccessor.class);
		testNotNull(() ->
			memoryClassLoader.getResourceAsStream(PropertyAccessor.class.getName().replace(".", "/") + ".class")
		);
	}
	
	@Test
	public void getResourceAsStreamTestTwo() throws ClassNotFoundException {
		MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);
		memoryClassLoader.loadOrUploadClass(PropertyAccessor.class);
		assertTrue(memoryClassLoader.hasPackageBeenDefined(PropertyAccessor.class.getPackage().getName()));
	}
	
}
