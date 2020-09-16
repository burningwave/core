package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.io.FileSystemItem;
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
	public void putPropertyOne() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = ((ComponentContainer)getComponentSupplier());
			componentContainer.getPathScannerClassLoader();
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER,
				Thread.currentThread().getContextClassLoader()
			);
		});
	}
	
	@Test
	public void putPropertyTwo() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = ((ComponentContainer)getComponentSupplier());
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION,
				FileSystemItem.CheckingOption.FOR_SIGNATURE_AND_NAME.getLabel()
			);
		});
	}
	
	@Test
	public void putPropertyThree() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = ((ComponentContainer)getComponentSupplier());
			componentContainer.setConfigProperty(
				ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER,
				"PathScannerClassLoader classLoader = PathScannerClassLoader.create(" +
					"((ComponentSupplier)parameter[0]).getPathScannerClassLoader()," +
					"((ComponentSupplier)parameter[0]).getPathHelper()," +
					"FileSystemItem.Criteria.forClassTypeFiles(" +
						"FileSystemItem.CheckingOption.FOR_SIGNATURE_AND_NAME" +
					")" +
				");" +
				"ManagedLoggersRepository.logInfo(\"ClassLoader {} succesfully created\", classLoader);" +
				"return classLoader;"	
			);
		});
	}
	
	@Test
	public void putPropertyFour() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = ((ComponentContainer)getComponentSupplier());
			componentContainer.setConfigProperty(
				ClassHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER,
				"PathScannerClassLoader classLoader = PathScannerClassLoader.create(" +
					"((ComponentSupplier)parameter[0]).getPathScannerClassLoader()," +
					"((ComponentSupplier)parameter[0]).getPathHelper()," +
					"FileSystemItem.Criteria.forClassTypeFiles(" +
						"FileSystemItem.CheckingOption.FOR_SIGNATURE_AND_NAME" +
					")" +
				");" +
				"ManagedLoggersRepository.logInfo(\"ClassLoader {} succesfully created\", classLoader);" +
				"return classLoader;"	
			);
		});
	}
}
