package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;


import org.burningwave.core.classes.MemoryClassLoader;
import org.junit.jupiter.api.Test;

public class ClassLoadersTest extends BaseTest {
	
	protected MemoryClassLoader getMemoryClassLoader() {
		return MemoryClassLoader.create(
			null
		);
	}
	
	@Test
	public void setAsParentClassLoader() {
		testNotNull(() -> {
			ClassLoader classLoader = getMemoryClassLoader();
			ClassLoaders.setAsParent(classLoader, Thread.currentThread().getContextClassLoader(), true);
			return Fields.get(classLoader, "parent");
		});
	}
	
}
