package org.burningwave.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

public class RepeatedComponentContainerTest extends ComponentContainerTest {
	
	@Test
	@Order(6)
	public void putPropertyFour() {
		testDoesNotThrow(() -> {
			assertTrue(true);
		});
	}
	
}
