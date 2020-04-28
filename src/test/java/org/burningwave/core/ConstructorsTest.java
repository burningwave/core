package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.service.ExtendedService;
import org.junit.jupiter.api.Test;


public class ConstructorsTest extends BaseTest {
	
	@Test
	public void newInstanceOfTestOne() {
		testNotNull(() -> Constructors.newInstanceOf(ExtendedService.class));
	}
	
	@Test
	public void newInstanceOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> Constructors.newInstanceOf(
				FunctionalInterfaceFactory.class, 
				componentSupplier.getClassFactory()
			)
		);
	}
}
