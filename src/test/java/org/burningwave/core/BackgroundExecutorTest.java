package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

public class BackgroundExecutorTest extends BaseTest {


	@Test
	public void killTestOne() {
		testDoesNotThrow(() -> {
			BackgroundExecutor.createTask(() -> {
				while(true) {}		
			}).submit().waitForFinish(5000).kill();
		});
	}
	
	@Test
	public void killTestTwo() {
		testDoesNotThrow(() -> {
			AtomicBoolean executed = new AtomicBoolean();
			BackgroundExecutor.createTask(() -> {
				while(true) {}			
			}).runOnlyOnce(
				UUID.randomUUID().toString(), executed::get
			).submit().waitForFinish(5000).kill();
		});
	}

}
