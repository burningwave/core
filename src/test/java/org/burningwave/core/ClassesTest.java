package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import org.junit.jupiter.api.Test;


public class ClassesTest extends BaseTest {

	@Test
	public void getDeclaredMethodTestOne() {
		testNotNull(() ->
			Classes.getDeclaredMethod(Classes.getClass(), method -> method.getName().equals("retrieveNames"))
		);
	}
	
	@Test
	public void getDeclaredConstructorTestOne() {
		testNotNull(() ->
			Classes.getDeclaredConstructor(Classes.getClass(), ctor -> ctor.getParameterCount() == 0)
		);
	}
	
}
