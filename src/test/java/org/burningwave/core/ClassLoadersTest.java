package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;

import org.burningwave.core.classes.MemoryClassLoader;
import org.junit.jupiter.api.Test;

public class ClassLoadersTest extends BaseTest {
	
	protected MemoryClassLoader getMemoryClassLoader(ClassLoader parent) {
		return MemoryClassLoader.create(
			parent
		);
	}
	
	@Test
	public void setAsParentClassLoader() {
		testNotNull(() -> {
			ClassLoader classLoader = getMemoryClassLoader(Thread.currentThread().getContextClassLoader());
			ClassLoaders.setAsParent(classLoader, Thread.currentThread().getContextClassLoader(), true);
			return ClassLoaders.getParent(classLoader);
		});
	}
	
	@Test
	public void setAsMaster() {
		testNotNull(() -> {
			ClassLoader classLoader_1 = getMemoryClassLoader(null);
			ClassLoader classLoader_2 = getMemoryClassLoader(classLoader_1);
			ClassLoader classLoader_3 = getMemoryClassLoader(classLoader_2);
			ClassLoader classLoader_4 = getMemoryClassLoader(null);
			ClassLoaders.setAsMaster(classLoader_3, classLoader_4, true);
			return ClassLoaders.getParent(classLoader_1);
		});
	}
	
	@Test
	public void getAsParentClassLoader() {
		testNotEmpty(() -> {
			ClassLoader classLoader = getMemoryClassLoader(null);
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
	
	@Test
	public void retrieveLoadedAllClassesTest() {
		testNotEmpty(() -> {
			return ClassLoaders.retrieveAllLoadedClasses(Thread.currentThread().getContextClassLoader());
		});
	}
}
