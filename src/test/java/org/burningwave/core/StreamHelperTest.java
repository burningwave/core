package org.burningwave.core;

import java.io.InputStream;

import org.burningwave.core.assembler.ComponentSupplier;
import org.junit.jupiter.api.Test;

public class StreamHelperTest extends BaseTest {
	
	@Test
	public void getResourceAsStreamTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() ->{ 
			try(InputStream inputStream = componentSupplier.getStreamHelper().getResourceAsStream("org/burningwave/core/classes/CodeExecutor.javatemplate")) {
				return inputStream;
			}
		});
	}
}
