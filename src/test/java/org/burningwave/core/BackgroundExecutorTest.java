package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.junit.jupiter.api.Test;

public class BackgroundExecutorTest extends BaseTest {


	@Test
	public void killTestOne() {
		assertTrue(
			BackgroundExecutor.createTask(() -> {
				while(true) {}		
			}).submit()
			.waitForStarting()
			.kill()
			.waitForTerminatedThreadNotAlive(100)
			.isTerminatedThreadNotAlive()
		);
	}
	
	@Test
	public void killTestTwo() {
		AtomicBoolean executed = new AtomicBoolean();
		AtomicReference<QueuedTasksExecutor.Task> childTask = new AtomicReference<>();
		QueuedTasksExecutor.Task mainTask = BackgroundExecutor.createTask(() -> {
			childTask.set(BackgroundExecutor.createTask(() -> {
				while(true){}
			}).runOnlyOnce(
				UUID.randomUUID().toString(), executed::get
			).submit());
			Thread.sleep(30000);
			executed.set(true);
		}).runOnlyOnce(
			UUID.randomUUID().toString(), executed::get
		).submit().waitForStarting().kill();
		assertTrue(
			mainTask.getInfoAsString(),
			mainTask.waitForTerminatedThreadNotAlive(100).isTerminatedThreadNotAlive()
		);
		assertTrue(
			childTask.get().getInfoAsString(),
			childTask.get().waitForTerminatedThreadNotAlive(100).isTerminatedThreadNotAlive()
		);
	}
	
	@Test
	public void interruptTestOne() {
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
	}
	
}
