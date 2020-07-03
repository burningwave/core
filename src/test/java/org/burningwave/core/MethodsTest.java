package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import org.burningwave.core.assembler.ComponentSupplier;
import org.junit.jupiter.api.Test;


public class MethodsTest extends BaseTest {
	
	@Test
	public void invokeAllTestOne() {
		testNotEmpty(
			() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				componentSupplier.clearHuntersCache(false);
				return Methods.invokeAll(Integer.class, "valueOf", 1);	
			}
		);
	}
	
	@Test
	public void invokeAllByCacheLoadingTestOne() {
		testNotEmpty(
			() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				componentSupplier.clearHuntersCache(false);
				return Methods.invokeAll(Integer.class, "valueOf", 1);	
			}
		);
	}
	
	@Test
	public void invokeVoidTestOne() {
		testDoesNotThrow(
			() -> {
				Methods.invoke(System.out, "println", "Hello World");	
			}
		);
	}
	
	@Test
	public void invokeDirectVoidTestOne() {
		testDoesNotThrow(
			() -> {
				Methods.invokeDirect(System.out, "println", "Hello World");	
			}
		);
	}
	
}
