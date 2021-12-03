package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.burningwave.core.concurrent.Thread;
import org.junit.jupiter.api.Test;

public class ThreadSupplierTest extends BaseTest {


	@Test
	public void setNullExecutableTestOne() {
		testThrow(() -> {
			ThreadSupplier.getOrCreateThread().setExecutable(null).start();
		});
	}
	
	@Test
	public void getPoolableThreadsTest() {
		AtomicInteger operationCount = new AtomicInteger(0);
		testDoesNotThrow(() -> {
			int iterationsCount = 100000;
			Thread.Supplier threadSupplier = Thread.Supplier.create(
				"ThreadSupplier for test",
				org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties,
				false
			);
			for (int i = 0; i < iterationsCount; i++) {
				Thread thread = threadSupplier.getOrCreatePoolableThread().setExecutable(thr -> {
					int currentOperationCount = operationCount.incrementAndGet();
					if (currentOperationCount % (iterationsCount / 5) == 0 && currentOperationCount != iterationsCount) {
						synchronized(thr) {
							long waitTime = 7500;
							ManagedLoggerRepository.logInfo(getClass()::getName, "Operation count: {} - Waiting for {}", currentOperationCount, waitTime);
							thr.wait(waitTime);
						}
					} else if (currentOperationCount % 10000 == 0) {
						ManagedLoggerRepository.logInfo(getClass()::getName, "Operation count: {}", currentOperationCount);
					}
				});
				thread.start();
			}
			threadSupplier.joinAllRunningThreads()
			.shutDownAllThreads(true);
			assertEquals(100000, operationCount.get());			
		});
	}
	
	//@Test
	public void stressTest() {
		testDoesNotThrow(() -> {
			int remainedRequestCount = 100_000_000;
			while (remainedRequestCount-- > 0) {
				final int remainedRequestCountTemp = remainedRequestCount;
				ThreadSupplier.getOrCreateThread().setExecutable(thread -> {
					//if (remainedRequestCountTemp % 100_000 == 0) {
						ManagedLoggerRepository.logInfo(getClass()::getName, "{} thread - Remained iterations: {}", thread.getClass().getSimpleName(), remainedRequestCountTemp);
					//}
				}).start();
			}			
		});
	}
}
