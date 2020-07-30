package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import java.util.Arrays;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.service.ExtendedService;
import org.junit.jupiter.api.Test;

@SuppressWarnings("all")
public class ConstructorsTest extends BaseTest {
	
	@Test
	public void newInstanceOfTestOne() {
		testNotNull(() -> Constructors.newInstanceDirectOf(ExtendedService.class));
	}
	
	@Test
	public void newInstanceOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> Constructors.newInstanceDirectOf(
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
			Constructors.newInstanceDirectOf(MemoryClassLoader.class, Thread.currentThread().getContextClassLoader())
		);
	}
	
	@Test
	public void newInstanceOfDirectTestTwo() {
		testNotNull(() ->
			Constructors.newInstanceDirectOf(MemoryClassLoader.class, null)
		);
	}
	
	@Test
	public void newInstanceOfDirectTestThree() {
		testNotNull(() ->
			Constructors.newInstanceDirectOf(SearchConfig.class, Arrays.asList(ComponentSupplier.getInstance().getPathHelper().getBurningwaveRuntimeClassPath()))
		);
	}
	
	@Test
	public void newInstanceOfTestThree() {
		testNotNull(() ->
			Constructors.newInstanceOf(SearchConfig.class, Arrays.asList(ComponentSupplier.getInstance().getPathHelper().getBurningwaveRuntimeClassPath()))
		);
	}
}
