package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

public class ComponentContainerTest extends BaseTest {

	@Test
	@Order(1)
	public void putPropertyOne() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = (getComponentSupplier());
			componentContainer.getPathScannerClassLoader();
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER,
				null
			);
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER,
				Thread.currentThread().getContextClassLoader()
			);
		});
	}

	@Test
	@Order(2)
	public void putPropertyTwo() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = (getComponentSupplier());
			componentContainer.setConfigProperty(
				PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION,
				FileSystemItem.CheckingOption.FOR_SIGNATURE_AND_NAME.getLabel()
			);
		});
	}


	@Test
	@Order(3)
	public void reInit() {
		testDoesNotThrow(() -> {
			getComponentSupplier().reInit();
		});
	}



	@Test
	@Order(4)
	public void putPropertyThree() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = (getComponentSupplier());
			componentContainer.setConfigProperty(
				ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER,
				"PathScannerClassLoader classLoader = PathScannerClassLoader.create(" +
					"((ComponentSupplier)parameter[0]).getPathScannerClassLoader()," +
					"((ComponentSupplier)parameter[0]).getPathHelper()," +
					"FileSystemItem.Criteria.forClassTypeFiles(" +
						"FileSystemItem.CheckingOption.FOR_SIGNATURE_AND_NAME" +
					")" +
				");" +
				"ManagedLoggerRepository.logInfo(() -> this.getClass().getName(), \"ClassLoader {} succesfully created\", classLoader);" +
				"return classLoader;"
			);
		});
	}


	@Test
	@Order(5)
	public void resetAndCloseTest() {
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
	@Order(6)
	public void putPropertyFour() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = (getComponentSupplier());
			componentContainer.setConfigProperty(
				ClassHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER,
				"PathScannerClassLoader classLoader = PathScannerClassLoader.create(" +
					"((ComponentSupplier)parameter[0]).getPathScannerClassLoader()," +
					"((ComponentSupplier)parameter[0]).getPathHelper()," +
					"FileSystemItem.Criteria.forClassTypeFiles(" +
						"FileSystemItem.CheckingOption.FOR_SIGNATURE_AND_NAME" +
					")" +
				");" +
				"ManagedLoggerRepository.logInfo(getClass()::getName, \"ClassLoader {} succesfully created\", classLoader);" +
				"return classLoader;"
			);
			componentContainer.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentContainer.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
				)
			);
		});
	}

	@Test
	@Order(7)
	public void clearAll() {
		testDoesNotThrow(() -> {
			org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Starting clearAll test", Runtime.getRuntime().totalMemory());
			org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Total memory at start {}", Runtime.getRuntime().totalMemory());
			ComponentContainer.resetAll();
			BackgroundExecutor.waitForTasksEnding();
			Cache.clear(true);
			System.gc();
			org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Total memory at finish {}", Runtime.getRuntime().totalMemory());
			MemoryClassLoader.DebugSupport.logAllInstancesInfo();
		});
	}

}
