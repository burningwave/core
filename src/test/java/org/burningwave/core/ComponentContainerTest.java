package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Cleaner;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.burningwave.core.assembler.ComponentContainer;
import org.junit.jupiter.api.Test;

public class ComponentContainerTest extends BaseTest {

	
	@Test
	public void reInitAndCloseTest() {
		testDoesNotThrow(() -> {
			ComponentContainer componentSupplier = ComponentContainer.create("burningwave.properties");
			componentSupplier.getClassFactory();
			componentSupplier.getClassHunter();
			componentSupplier.getClassPathHunter();
			componentSupplier.getCodeExecutor();
			componentSupplier.getPathHelper();
			GlobalProperties.put("newPropertyName", "newPropertyValue");
			componentSupplier.reInit();
			componentSupplier.getClassFactory();
			componentSupplier.getClassHunter();
			componentSupplier.getClassPathHunter();
			componentSupplier.getCodeExecutor();
			componentSupplier.getPathHelper();
			componentSupplier.close();
		});
	}
	
	@Test
	public void clearAll() {
		logWarn("Total memory before clearAll {}", Runtime.getRuntime().totalMemory());
		ComponentContainer.clearAll();
		Cleaner.waitForExecutorsEnding();
		logWarn("Total memory after clearAll {}", Runtime.getRuntime().totalMemory());
	}
	
}
