package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ConstructorHelper;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.service.ExtendedService;
import org.junit.jupiter.api.Test;


public class ConstructorHelperTest extends BaseTest {
	
	@Test
	public void newInstanceOfTestOne() {
		testNotNull(() -> ConstructorHelper.newInstanceOf(ExtendedService.class));
	}
	
	@Test
	public void newInstanceOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> ConstructorHelper.newInstanceOf(
				FunctionalInterfaceFactory.class, 
				componentSupplier.getClassFactory()
			)
		);
	}
}
