package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier;

import org.junit.jupiter.api.Test;

public class ThreadSupplierTest extends BaseTest {


	@Test
	public void setNullExecutableTestOne() {
		testThrow(() -> {
			ThreadSupplier.getOrCreate().setExecutable(null).start();
		});
	}
	
	//@Test
	public void stressTest() {
		testDoesNotThrow(() -> {
			int remainedRequestCount = 100_000_000;
			while (remainedRequestCount-- > 0) {
				final int remainedRequestCountTemp = remainedRequestCount;
				ThreadSupplier.getOrCreate().setExecutable(thread -> {
					//if (remainedRequestCountTemp % 100_000 == 0) {
						ManagedLoggerRepository.logInfo(getClass()::getName, "{} thread - Remained iteration: {}", thread.getClass().getSimpleName(), remainedRequestCountTemp);
					//}
				}).start();
			}			
		});
	}
}
