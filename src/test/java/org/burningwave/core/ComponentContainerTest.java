package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.junit.jupiter.api.Test;

public class ComponentContainerTest extends BaseTest {

	
	@Test
	public void resetAndCloseTest() {
		testDoesNotThrow(() -> {
			ComponentContainer componentSupplier = ComponentContainer.create("burningwave.properties");
			componentSupplier.getClassFactory();
			componentSupplier.getClassHunter();
			componentSupplier.getClassPathHunter();
			componentSupplier.getCodeExecutor();
			componentSupplier.getPathHelper();
			GlobalProperties.put("newPropertyName", "newPropertyValue");
			componentSupplier.reset();
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
		testDoesNotThrow(() -> {
			logWarn("Total memory before clearAll {}", Runtime.getRuntime().totalMemory());
			ComponentContainer.clearAll();
			BackgroundExecutor.waitForTasksEnding();
			System.gc();
			logWarn("Total memory after clearAll {}", Runtime.getRuntime().totalMemory());
		});
	}
	
	
	@Test
	public void reset() {
		testDoesNotThrow(() -> {
			getComponentSupplier().reset();
		});
	}
	
	@Test
	public void putProperty() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = ((ComponentContainer)getComponentSupplier());
			componentContainer.getPathScannerClassLoader();
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER,
				Thread.currentThread().getContextClassLoader()
			);
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION,
				"checkFileSignature"
			);
		});
	}
}
