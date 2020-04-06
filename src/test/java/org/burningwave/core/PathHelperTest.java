package org.burningwave.core;

import java.io.InputStream;

import org.burningwave.core.assembler.ComponentSupplier;
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
}
