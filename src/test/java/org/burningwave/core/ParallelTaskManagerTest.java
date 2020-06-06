package org.burningwave.core;

import org.burningwave.core.concurrent.ParallelTasksManager;
import org.junit.jupiter.api.Test;

public class ParallelTaskManagerTest extends BaseTest{
	
	@Test
	public void addAndWaitTest() {
		testDoesNotThrow(() -> {
			ParallelTasksManager taskManager = ParallelTasksManager.create();
			final Integer taskCount = 1000;
			for (int i = 0; i < taskCount; i++) {
				taskManager.execute(() -> {
					Thread.sleep(25);
				});
			}
			logInfo("Wait for {} tasks ending", taskCount);
			taskManager.waitForTasksEnding();
			logInfo("Tasks have ended execution", taskCount);
		});
	}
	
}
