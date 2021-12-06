package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ByteCodeHunter.SearchResult;
import org.burningwave.core.classes.Classes;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.PathHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class ClassLoadersTest extends BaseTest {

	protected MemoryClassLoader getMemoryClassLoader(ClassLoader parent) {
		return MemoryClassLoader.create(
			parent
		);
	}

	@Test
	//@DisabledOnOs(OS.LINUX)
	public void setAsParentClassLoaderTest() {
		if (System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).indexOf("nux") >= 0) {
			testNotNull(() -> {
				try (MemoryClassLoader classLoader = getMemoryClassLoader(null);) {
					ClassLoaders.setAsParent(classLoader, Thread.currentThread().getContextClassLoader());
					return ClassLoaders.getParent(classLoader);
				}
			});
		}
	}

	@Test
	@DisabledOnOs(OS.LINUX)
	public void setAsMasterTest() {
		testDoesNotThrow(() -> {
			MemoryClassLoader classLoader_1 = getMemoryClassLoader(null);
			MemoryClassLoader classLoader_2 = getMemoryClassLoader(classLoader_1);
			MemoryClassLoader classLoader_3 = getMemoryClassLoader(classLoader_2);
			MemoryClassLoader classLoader_4 = getMemoryClassLoader(null);
			Function<Boolean, ClassLoader> resetter = ClassLoaders.setAsMaster(classLoader_3, classLoader_4);
			assertEquals(ClassLoaders.getParent(classLoader_1), classLoader_4);
			resetter.apply(true);
			assertEquals(ClassLoaders.getParent(classLoader_1), null);
			classLoader_4.close();
			classLoader_3.close();
			classLoader_2.close();
			classLoader_1.close();
		});
	}

	@Test
	@DisabledOnOs(OS.LINUX)
	public void getAsParentClassLoaderTest() {
		testNotEmpty(() -> {
			try(MemoryClassLoader classLoader = getMemoryClassLoader(null)) {
				ClassLoaders.setAsParent(classLoader, Thread.currentThread().getContextClassLoader());
				return ClassLoaders.getAllParents(classLoader);
			}
		});
	}

	@Test
	//@DisabledOnOs(OS.LINUX)
	public void addClassPathsTestOne() {
		testNotNull(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			ClassLoaders.addClassPath(classLoader, pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/commons-lang"));
			Class<?> cls = classLoader.loadClass("org.apache.commons.lang.ArrayUtils");
			return cls;
		});
	}

	@Test
	//@DisabledOnOs(OS.LINUX)
	public void addClassPathsTestTwo() {
		testNotNull(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			ClassLoader urlClassLoader = Thread.currentThread().getContextClassLoader();
			ClassLoaders.addClassPath(urlClassLoader, pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar"));
			Class<?> cls = urlClassLoader.loadClass("org.springframework.core.serializer.DefaultSerializer");
			return cls;
		});
	}

	@Test
	@DisabledOnOs(OS.LINUX)
	public void loadOrDefineByByteCodesTestOne() {
		testNotNull(() -> {
			try(MemoryClassLoader classLoader = getMemoryClassLoader(null)) {
				ComponentSupplier componentSupplier = getComponentSupplier();
				PathHelper pathHelper = componentSupplier.getPathHelper();
				try (SearchResult searchResult = componentSupplier.getByteCodeHunter().findBy(
					SearchConfig.forPaths(
							pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/commons-lang")
					)
				)) {
					return ClassLoaders.loadOrDefineByByteCode(searchResult.getByteCodesFlatMap().get("org.apache.commons.lang.ArrayUtils"), classLoader);
				}
			}
		});
	}

	@Test
	@DisabledOnOs(OS.LINUX)
	public void loadOrDefineByByteCodesTestTwo() {
		testNotNull(() -> {
			try(MemoryClassLoader classLoader = getMemoryClassLoader(null)) {
				ComponentSupplier componentSupplier = getComponentSupplier();
				PathHelper pathHelper = componentSupplier.getPathHelper();
				try (SearchResult searchResult = componentSupplier.getByteCodeHunter().findBy(
					SearchConfig.forPaths(
							pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/commons-lang")
					)
				)) {
					Map<String, ByteBuffer> byteCodesFound = searchResult.getByteCodesFlatMap();
					Map<String, JavaClass> byteCodes = new HashMap<>();
					return JavaClass.extractByUsing(byteCodesFound.get("org.apache.commons.lang.ArrayUtils"), (javaClass) -> {
						byteCodes.put("org.apache.commons.lang.ArrayUtils", javaClass);
						return ClassLoaders.loadOrDefineByJavaClass("org.apache.commons.lang.ArrayUtils", byteCodes, classLoader);
					});
				}
			}
		});
	}

	@Test
	@DisabledOnOs(OS.LINUX)
	public void createAndClose() {
		testDoesNotThrow(() -> {
			Classes.Loaders.create().close();
		});
	}

}
