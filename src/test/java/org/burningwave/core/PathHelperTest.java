package org.burningwave.core;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.Collection;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;
import org.junit.jupiter.api.Test;

public class PathHelperTest extends BaseTest {
	
	@Test
	public void getResourceAsStreamTestOne() {
		testNotNull(() ->{ 
			ComponentSupplier componentSupplier = getComponentSupplier();
			try(InputStream inputStream = componentSupplier.getPathHelper().getResourceAsStream("org/burningwave/core/classes/ClassLoaderDelegate.bwc")) {
				return inputStream;
			}
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
