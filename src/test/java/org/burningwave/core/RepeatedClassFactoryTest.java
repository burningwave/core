package org.burningwave.core;

import org.burningwave.core.assembler.ComponentContainer;
import org.junit.jupiter.api.Test;

public class RepeatedClassFactoryTest extends ClassFactoryTest {
	
	@Test
	public void getOrBuildClassWithExternalClassLastTest() {
		logWarn("Total memory before clearAll {}", Runtime.getRuntime().totalMemory());
		ComponentContainer.clearAll();
		System.gc();
		logWarn("Total memory after clearAll {}", Runtime.getRuntime().totalMemory());
		testDoesNotThrow(() -> {
			getOrBuildClassWithExternalClassTestOne(true, null);
		});
	}
	
}
