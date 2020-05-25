package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ByFieldOrByMethodPropertyAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.burningwave.core.bean.Complex;
import org.junit.jupiter.api.Test;

public class PropertyAccessorTest extends BaseTest {
	
	@Test
	public void getTestOne() {
		Complex complex = new Complex();
		assertNotNull(ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[1][1].name"));
		assertNotNull(ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name"));
	}
	
	
	@Test
	public void setTestOne() {
		Complex complex = new Complex();
		String newName = "Peter";
		ByFieldOrByMethodPropertyAccessor.set(complex, "data.items[0][2].name", newName);
		assertEquals(ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[0][2].name"), newName);
	}

	
	@Test
	void setTestTwo() {
		Complex complex = new Complex();
		Complex.Data.Item newItem = new Complex.Data.Item("Sam");
		ByFieldOrByMethodPropertyAccessor.set(complex, "data.items[0][1]", newItem);
		assertEquals(ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[0][1]"), newItem);
	}
	
	
	@Test
	void setTestThree() {
		Complex complex = new Complex();
		ByFieldOrByMethodPropertyAccessor.set(complex, "data.itemsMap[items]",
			ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items]")
		);
		assertEquals(
			(Object)ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items]"),
			(Object)ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items]")
		);
	}
	
	@Test
	void setTestFour() {
		Complex complex = new Complex();
		ByFieldOrByMethodPropertyAccessor.set(complex, "data",
			ByFieldOrByMethodPropertyAccessor.get(complex, "data")
		);
		assertEquals(
			(Object)ByFieldOrByMethodPropertyAccessor.get(complex, "data"), 
			(Object)ByFieldOrByMethodPropertyAccessor.get(complex, "data")
		);
	}
	
}
