package org.burningwave.core;

import org.burningwave.core.jvm.LowLevelObjectsHandler;
import org.junit.jupiter.api.Test;

public class LowLevelObjectsHandlerTest extends BaseTest {

	
	@Test
	public void createAndClose() {
		testDoesNotThrow(() -> {
			LowLevelObjectsHandler.create().close();
		});
	}
	
}
