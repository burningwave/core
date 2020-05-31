package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ByteCodeHunter.SearchResult;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.PathHelper;
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
			ClassLoader classLoader = getMemoryClassLoader(null);
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
	
	@Test
	public void retrieveLoadedAllClassesTestTwo() {
		testNotEmpty(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			SearchResult searchResult = componentSupplier.getByteCodeHunter().loadInCache(
				SearchConfig.forPaths(
						pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/commons-lang")
				)
			).find();
			Map<String, ByteBuffer> byteCodesFound = searchResult.getByteCodesFlatMap();
			Map<String, ByteBuffer> byteCodes = new HashMap<>();
			byteCodes.put("org.apache.commons.lang.ArrayUtils", byteCodesFound.get("org.apache.commons.lang.ArrayUtils"));
			return ClassLoaders.loadOrDefineByByteCodes(byteCodes, getMemoryClassLoader(null)).values();
		});
	}

}
