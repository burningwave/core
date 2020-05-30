package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;

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
			return ClassLoaders.getParent(classLoader);
		});
	}
	
	@Test
	public void getAsParentClassLoader() {
		testNotEmpty(() -> {
			ClassLoader classLoader = getMemoryClassLoader();
			ClassLoaders.setAsParent(classLoader, Thread.currentThread().getContextClassLoader(), true);
			return ClassLoaders.getAllParents(classLoader);
		});
	}
	
	@Test
	public void retrieveLoadedClassesForPackage() {
		testNotEmpty(() -> {
			return ClassLoaders.retrieveLoadedClassesForPackage(Thread.currentThread().getContextClassLoader(), (pckg) -> pckg != null);
		});
	}
	
	@Test
	public void retrieveLoadedClassesTest() {
		testNotEmpty(() -> {
			return ClassLoaders.retrieveLoadedClasses(Thread.currentThread().getContextClassLoader());
		});
	}
}
