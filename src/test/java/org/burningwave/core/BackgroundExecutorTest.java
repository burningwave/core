package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.burningwave.core.concurrent.QueuedTaskExecutor;
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
				.waitForTerminatedThreadNotAlive(100, 3000)
				.isTerminatedThreadNotAlive()
			);
		});
	}

	//@Test
	public void stressTestOne() {
		testDoesNotThrow(() -> {
			AtomicInteger remainedRequestCountWrapper = new AtomicInteger(100_000_000);
			Random random = new Random();
			Collection<QueuedTaskExecutor.Task> tasks = new LinkedHashSet<>();
			for (int i = 0; i < 10; i++) {
				tasks.add(
					BackgroundExecutor.createTask(tsk -> {
						while (remainedRequestCountWrapper.get() > 0) {
							BackgroundExecutor.createTask(task -> {
								int remainedRequestCount = remainedRequestCountWrapper.getAndDecrement();
								if (remainedRequestCount % 100_000 == 0) {
									ManagedLoggerRepository.logInfo(getClass()::getName, "Remained iteration: {}", remainedRequestCount);
								}
							}, random.ints(Thread.NORM_PRIORITY, Thread.MAX_PRIORITY + 1).findFirst().getAsInt()).submit();
						}
					}).submit()
				);
			}
			tasks.forEach(QueuedTaskExecutor.Task::waitForFinish);
		});
	}

	//@Test
	public void stressTestTwo() {
		testDoesNotThrow(() -> {
			int remainedRequestCount = 100_000_000;
			Random random = new Random();
			while (remainedRequestCount-- > 0) {
				final int remainedRequestCountTemp = remainedRequestCount;
				BackgroundExecutor.createTask(task -> {
					if (remainedRequestCountTemp % 100_000 == 0) {
						ManagedLoggerRepository.logInfo(getClass()::getName, "Remained iteration: {}", remainedRequestCountTemp);
					}
				}, random.ints(Thread.NORM_PRIORITY, Thread.MAX_PRIORITY + 1).findFirst().getAsInt()).submit();
			}
		});
	}

	@Test
	public void killTestTwo() {
		testDoesNotThrow(() -> {
			AtomicBoolean executed = new AtomicBoolean(false);
			AtomicReference<QueuedTaskExecutor.Task> mainTaskWrapper = new AtomicReference<>();
			QueuedTaskExecutor.Task childTask = BackgroundExecutor.createTask(task -> {
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
				mainTaskWrapper.get().waitForTerminatedThreadNotAlive(100, 3000).isTerminatedThreadNotAlive() && !executed.get()
			);
			assertTrue(
				childTask.getInfoAsString(),
				childTask.waitForTerminatedThreadNotAlive(100, 3000).isTerminatedThreadNotAlive()
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
