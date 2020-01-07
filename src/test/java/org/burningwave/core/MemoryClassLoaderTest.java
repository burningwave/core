package org.burningwave.core;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.reflection.PropertyAccessor;
import org.junit.jupiter.api.Test;

public class MemoryClassLoaderTest extends BaseTest {
	
	protected MemoryClassLoader getMemoryClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		return MemoryClassLoader.create(
			null, componentSupplier.getClassHelper()
		);
	}
	
	@Test
	public void loadClassTestOne() {
		testNotNull(() ->
			getMemoryClassLoader().loadOrUploadClass(PropertyAccessor.class)
		);
	}
	
	@Test
	public void getResourceAsStreamTestOne() throws ClassNotFoundException {
		MemoryClassLoader memoryClassLoader = getMemoryClassLoader();
		memoryClassLoader.loadOrUploadClass(PropertyAccessor.class);
		testNotNull(() ->
			memoryClassLoader.getResourceAsStream(PropertyAccessor.class.getName().replace(".", "/") + ".class")
		);
	}
	
}
