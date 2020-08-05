package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ByteBufferHandler;
import org.junit.jupiter.api.Test;

public class ByteBufferHandlerTest extends BaseTest {

	
	@Test
	public void getAddressTest() {
		testNotNull(() -> {
			Long.valueOf(ByteBufferHandler.getAddress(ByteBufferHandler.allocateDirect(1)));
			return Long.valueOf(ByteBufferHandler.getAddress(ByteBufferHandler.allocateDirect(1)));
		});
	}
	
}
