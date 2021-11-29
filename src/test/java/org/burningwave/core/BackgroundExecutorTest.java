package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

public class BackgroundExecutorTest extends BaseTest {


	@Test
	public void killTestOne() {
		assertTrue(
			BackgroundExecutor.createTask(() -> {
				while(true) {}		
			}).submit().waitForFinish(5000).kill().isAborted()
		);
	}
	
	@Test
	public void killTestTwo() {
		AtomicBoolean executed = new AtomicBoolean();
		assertTrue(			
			BackgroundExecutor.createTask(() -> {
				Thread.sleep(100000);		
				executed.set(true);
			}).runOnlyOnce(
				UUID.randomUUID().toString(), executed::get
			).submit().waitForFinish(5000).kill().isAborted()
		);
	}

}
