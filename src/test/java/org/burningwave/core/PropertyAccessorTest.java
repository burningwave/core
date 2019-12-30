package org.burningwave.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.junit.jupiter.api.Test;

public class PropertyAccessorTest extends BaseTest {
	
	@Test
	public void getTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Complex complex = new Complex();
		assertNotNull(componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.items[1][1].name"));
		assertNotNull(componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.itemsMap[items][1][1].name"));
	}
	
	
	@Test
	public void setTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Complex complex = new Complex();
		String newName = "Peter";
		componentSupplier.getByFieldOrByMethodPropertyAccessor().set(complex, "data.items[0][2].name", newName);
		assertEquals(componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.items[0][2].name"), newName);
	}

	
	@Test
	void setTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Complex complex = new Complex();
		Complex.Data.Item newItem = new Complex.Data.Item("Sam");
		componentSupplier.getByFieldOrByMethodPropertyAccessor().set(complex, "data.items[0][1]", newItem);
		assertEquals(componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.items[0][1]"), newItem);
	}
	
	
	@Test
	void setTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Complex complex = new Complex();
		componentSupplier.getByFieldOrByMethodPropertyAccessor().set(complex, "data.itemsMap[items]",
			componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.itemsMap[items]")
		);
		assertEquals(
			(Object)componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.itemsMap[items]"),
			(Object)componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data.itemsMap[items]")
		);
	}
	
	@Test
	void setTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Complex complex = new Complex();
		componentSupplier.getByFieldOrByMethodPropertyAccessor().set(complex, "data",
			componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data")
		);
		assertEquals(
			(Object)componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data"), 
			(Object)componentSupplier.getByFieldOrByMethodPropertyAccessor().get(complex, "data")
		);
	}
	
}
