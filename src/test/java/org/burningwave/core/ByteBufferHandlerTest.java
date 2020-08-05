package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ByteBufferHandler;

import org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferHandler.Deallocator;
import org.junit.jupiter.api.Test;

public class ByteBufferHandlerTest extends BaseTest {

	
	@Test
	public void getAddressTest() {
		testNotNull(() -> {
			Long.valueOf(ByteBufferHandler.getAddress(ByteBufferHandler.allocateDirect(1)));
			return Long.valueOf(ByteBufferHandler.getAddress(ByteBufferHandler.allocateDirect(1)));
		});
	}
	
	@Test
	public void getDeallocatorTest() {
		testDoesNotThrow(() -> {
			Deallocator deallocator = ByteBufferHandler.getDeallocator(ByteBufferHandler.allocateDirect(1));
			deallocator.freeMemory();
			deallocator.freeMemory();
			ByteBufferHandler.getDeallocator(ByteBufferHandler.allocateDirect(1)).freeMemory();
		});
	}
	
}
