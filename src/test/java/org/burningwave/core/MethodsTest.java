package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import org.burningwave.core.assembler.ComponentSupplier;
import org.junit.jupiter.api.Test;


public class MethodsTest extends BaseTest {
	
	@Test
	public void invokeAllTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearHuntersCache(false);
		testNotEmpty(
			() -> {
				return Methods.invokeAll(Integer.class, "valueOf", 1);
					
			}
		);
	}
	
}
