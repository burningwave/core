package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.junit.jupiter.api.Test;

public class BackgroundExecutorTest extends BaseTest {


	@Test
	public void killTestOne() {
		testDoesNotThrow(() -> {
			assertTrue(
				BackgroundExecutor.createTask(() -> {
					while(true) {}		
				}).submit()
				.waitForStarting()
				.kill()
				.waitForTerminatedThreadNotAlive(100)
				.isTerminatedThreadNotAlive()
			);
		});
	}
	
	//@Test
	public void stressTest() {
		testDoesNotThrow(() -> {
			int remainedRequestCount = 100_000_000;
			Random random = new Random();
			AtomicReference<Throwable> exceptionWrapper = new AtomicReference<>();
			while (remainedRequestCount-- > 0 && exceptionWrapper.get() == null) {
				final int remainedRequestCountTemp = remainedRequestCount;
				BackgroundExecutor.createTask(task -> {
					//if (remainedRequestCountTemp % 100_000 == 0) {
						ManagedLoggerRepository.logInfo(getClass()::getName, "Remained iteration: {}", remainedRequestCountTemp);
					//}
				}, random.nextInt(Thread.MAX_PRIORITY) + 1).submit();
			}			
		});
	}
	
	@Test
	public void killTestTwo() {
		testDoesNotThrow(() -> {
			AtomicBoolean executed = new AtomicBoolean(false);
			AtomicReference<QueuedTasksExecutor.Task> mainTaskWrapper = new AtomicReference<>();
			QueuedTasksExecutor.Task childTask = BackgroundExecutor.createTask(task -> {
				Thread.sleep(2500);
				mainTaskWrapper.get().kill();
				while(true){}
			});
			mainTaskWrapper.set(BackgroundExecutor.createTask(task -> {
				childTask.runOnlyOnce(
					UUID.randomUUID().toString(), executed::get
				).submit();
				Thread.sleep(5000);
				executed.set(true);
			}).runOnlyOnce(
				UUID.randomUUID().toString(), executed::get
			).submit().waitForStarting());
			assertTrue(
				mainTaskWrapper.get().getInfoAsString(),
				mainTaskWrapper.get().waitForTerminatedThreadNotAlive(100).isTerminatedThreadNotAlive() && !executed.get()
			);
			assertTrue(
				childTask.getInfoAsString(),
				childTask.waitForTerminatedThreadNotAlive(100).isTerminatedThreadNotAlive()
			);
		});
	}
	
	@Test
	public void interruptTestOne() {
		testDoesNotThrow(() -> {
			AtomicBoolean executed = new AtomicBoolean();
			assertTrue(			
				!BackgroundExecutor.createTask(() -> {
					Thread.sleep(10000);		
					executed.set(true);
				}).runOnlyOnce(
					UUID.randomUUID().toString(), executed::get
				).submit()
				.waitForStarting()
				.interrupt()
				.waitForFinish()
				.wasExecuted()
			);
		});
	}
	
}
