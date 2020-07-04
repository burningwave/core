package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.service.ExtendedService;
import org.junit.jupiter.api.Test;


public class ConstructorsTest extends BaseTest {
	
	@Test
	public void newInstanceOfTestOne() {
		testNotNull(() -> Constructors.newInstanceOfDirect(ExtendedService.class));
	}
	
	@Test
	public void newInstanceOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> Constructors.newInstanceOfDirect(
				FunctionalInterfaceFactory.class, 
				componentSupplier.getClassFactory()
			)
		);
	}
	
	@Test
	public void convertToMethodHandleTestOne() {
		testNotNull(() ->
			Constructors.convertToMethodHandle(
				Constructors.findOneAndMakeItAccessible(ExtendedService.class)
			).invoke()			
		);
	}
	
	@Test
	public void newInstanceOfDirectTestOne() {
		testNotNull(() ->
			Constructors.newInstanceOfDirect(MemoryClassLoader.class, Thread.currentThread().getContextClassLoader())
		);
	}
}
