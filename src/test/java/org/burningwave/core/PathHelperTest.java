package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.junit.jupiter.api.Test;

public class PathHelperTest extends BaseTest {
	
	@Test
	public void getResourceAsStreamTestOne() {
		testNotNull(() ->{ 
			ComponentSupplier componentSupplier = getComponentSupplier();
			try(InputStream inputStream = componentSupplier.getPathHelper().getResourceAsStream("org/burningwave/core/jvm/ClassLoaderDelegateForJDK9.bwc")) {
				return inputStream;
			}
		});
	}
	
	@Test
	public void getResourceAsStreamTestTwo() {
		testNotNull(() ->{ 
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			Collection<Thread> threads = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				for (int j = 0; j < 10; j++) {
					Thread thread = new Thread(() -> {
						FileSystemItem fIS = pathHelper.getResource("../../src/test/external-resources/libs-for-test.zip");
						fIS.exists();
						fIS.reset();
					});
					thread.start();
					threads.add(thread);
				}
			}
			for (Thread thread : threads) {
				thread.join();
			}
			return pathHelper .getResource("../../src/test/external-resources/libs-for-test.zip");
		});
	}
	
	@Test
	public void getResourceTestOne() {
		testNotNull(() ->{ 
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			return pathHelper.getResource("burningwave.properties");
		});
	}
	
	@Test
	public void getResourceAsStringBufferTestOne() {
		testNotNull(() ->{ 
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			StringBuffer fileAsString = pathHelper.getResourceAsStringBuffer("burningwave.properties");
			ManagedLoggersRepository.logDebug(getClass()::getName, fileAsString.toString());
			return fileAsString;
		});
	}
	
	@Test
	public void getResourceCustomClassPath() {
		testNotEmpty(() ->{
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			return pathHelper.getPaths("custom-class-path");
		});
	}
	
	@Test
	public void getResourceCustomClassPathTestTwo() {
		testNotEmpty(() -> { 
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathHelper pathHelper = componentSupplier.getPathHelper();
			return pathHelper.getPaths("custom-class-path2");
		});
	}
	
	@Test
	public void optimizePathsTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		Collection<String> paths = pathHelper.optimize(
			pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear"),
			pathHelper.getAbsolutePathOfResource("../../src/test/external-resources"),
			pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
		);
		assertEquals(paths.size(), 1);
		assertTrue(paths.iterator().next().endsWith("/src/test/external-resources"));
	}
	
}
