package org.burningwave.core;

import org.burningwave.core.concurrent.ParallelTasksManager;
import org.junit.jupiter.api.Test;

public class ParallelTaskManagerTest extends BaseTest{
	
	@Test
	public void addAndWaitTest() {
		ParallelTasksManager taskManager = ParallelTasksManager.create();
		for (int i = 0; i < 1000; i++) {
			final Integer idx = i + 1;
			taskManager.execute(() -> {
				Thread.sleep(25);
				logInfo("task {} executed", idx);
			});
			logInfo("task {} launched", idx);
		}
		taskManager.waitForTasksEnding();
	}
	
}
