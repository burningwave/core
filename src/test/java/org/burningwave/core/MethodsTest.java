package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import org.burningwave.core.assembler.ComponentSupplier;
import org.junit.jupiter.api.Test;


public class MethodsTest extends BaseTest {
	
	@Test
	public void invokeTestOne() {
		testNotNull(
			() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				componentSupplier.clearHuntersCache(false);
				return Methods.invoke(Integer.class, "valueOf", 1);	
			}
		);
	}
	
	@Test
	public void findAllAndMakeThemAccessibleTestOne() {
		testNotEmpty(
			() -> {
				Methods.findAllAndMakeThemAccessible(System.out);	
				return Methods.findAllAndMakeThemAccessible(System.out);	
			},
		true);
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
