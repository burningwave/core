package org.burningwave.core;

import java.io.InputStream;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;
import org.junit.jupiter.api.Test;

public class PathHelperTest extends BaseTest {
	
	@Test
	public void getResourceAsStreamTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() ->{ 
			try(InputStream inputStream = componentSupplier.getPathHelper().getResourceAsStream("org/burningwave/core/classes/ClassLoaderDelegate.bwc")) {
				return inputStream;
			}
		});
	}
	
	@Test
	public void getResourceCustomClassPath() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(() ->{ 
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
	
}
