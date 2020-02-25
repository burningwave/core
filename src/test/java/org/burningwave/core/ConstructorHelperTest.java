package org.burningwave.core;


import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.Classes;
import org.burningwave.core.classes.ConstructorHelper;
import org.burningwave.core.reflection.ConsumerBinder;
import org.burningwave.core.service.ExtendedService;
import org.junit.jupiter.api.Test;


public class ConstructorHelperTest extends BaseTest {
	
	@Test
	public void newInstanceOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getConstructorHelper().newInstanceOf(ExtendedService.class));
	}
	
	@Test
	public void newInstanceOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getConstructorHelper().newInstanceOf(
			ConstructorHelper.class,
			Classes.getInstance(),
			componentSupplier.getMemberFinder()
		));
	}
	
	@Test
	public void newInstanceOfTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getConstructorHelper().newInstanceOf(
			ConsumerBinder.class,
			componentSupplier.getMemberFinder(), 
			componentSupplier.getConsulterRetriever(), null)
		);
	}
}
