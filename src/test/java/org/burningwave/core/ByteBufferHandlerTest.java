package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;

import org.burningwave.core.jvm.BufferHandler.Deallocator;
import org.junit.jupiter.api.Test;

public class ByteBufferHandlerTest extends BaseTest {

	
	@Test
	public void getAddressTest() {
		testNotNull(() -> {
			Long.valueOf(BufferHandler.getAddress(BufferHandler.allocateDirect(1)));
			return Long.valueOf(BufferHandler.getAddress(BufferHandler.allocateDirect(1)));
		});
	}
	
	@Test
	public void getDeallocatorTest() {
		testDoesNotThrow(() -> {
			Deallocator deallocator = BufferHandler.getDeallocator(BufferHandler.allocateDirect(1).duplicate(), true);
			deallocator.freeMemory();
			deallocator.freeMemory();
			BufferHandler.getDeallocator(BufferHandler.allocateDirect(1), false).freeMemory();
		});
	}
	
	@Test
	public void destroyTest() {
		testDoesNotThrow(() -> {
			BufferHandler.destroy(BufferHandler.allocateDirect(1).duplicate(), true);
		});
	}
	
}
