package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.burningwave.core.assembler.ComponentContainer;
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
	
	@Test
	@Order(7)
	public void clearAll() {
		testDoesNotThrow(() -> {
			org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository.logWarn(getClass()::getName, "Total memory before clearAll {}", Runtime.getRuntime().totalMemory());
			ComponentContainer.resetAll();
			Cache.clear(true);
			BackgroundExecutor.waitForTasksEnding(true);
			System.gc();
			org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository.logWarn(getClass()::getName, "Total memory after clearAll {}", Runtime.getRuntime().totalMemory());
		});
	}

}
