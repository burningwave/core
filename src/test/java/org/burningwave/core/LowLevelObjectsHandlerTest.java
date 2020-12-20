package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.LowLevelObjectsHandler;

import java.io.PrintStream;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class LowLevelObjectsHandlerTest extends BaseTest {

	
	@Test
	public void createAndCloseTest() {
		testDoesNotThrow(() -> {
			org.burningwave.core.jvm.LowLevelObjectsHandler.create().close();
		});
	}
	
	@Test
	public void invokeTest() {
		testDoesNotThrow(() -> {
			Method printlnMethod = LowLevelObjectsHandler.getDeclaredMethod(PrintStream.class, method -> 
				method.getName().equals("println") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].equals(String.class)
			);
			LowLevelObjectsHandler.invoke(System.out, printlnMethod, "Hello World!");
		});
	}
	
}
