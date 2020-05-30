package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import org.junit.jupiter.api.Test;

public class ClassesTest extends BaseTest {

	
	@Test
	public void getIdTest() {
		testNotNull(() -> {
			return Classes.getId(
				Thread.currentThread().getContextClassLoader(),
				new String("id"),
				Integer.valueOf(1),
				Long.valueOf(2),
				Double.valueOf(3),
				Float.valueOf(5),
				Short.valueOf("1"),
				Byte.valueOf((byte) 1),
				Character.valueOf('c'),
				true
			);
		});
	}
	
}
