package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class ComponentContainerTest extends BaseTest {

	
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
	@Order(17)
	public void clearAll() {
		testDoesNotThrow(() -> {
			ManagedLoggersRepository.logWarn(getClass()::getName, "Total memory before clearAll {}", Runtime.getRuntime().totalMemory());
			ComponentContainer.clearAll();
			BackgroundExecutor.waitForTasksEnding(true);
			System.gc();
			ManagedLoggersRepository.logWarn(getClass()::getName, "Total memory after clearAll {}", Runtime.getRuntime().totalMemory());
		});
	}
	
	
	@Test
	@Order(3)
	public void reset() {
		testDoesNotThrow(() -> {
			getComponentSupplier().reset();
		});
	}
	
	@Test
	@Order(1)
	public void putPropertyOne() {
		testDoesNotThrow(() -> {
			ComponentContainer componentContainer = (getComponentSupplier());
			componentContainer.getPathScannerClassLoader();
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
				"ManagedLoggersRepository.logInfo(() -> this.getClass().getName(), \"ClassLoader {} succesfully created\", classLoader);" +
				"return classLoader;"	
			);
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
				"ManagedLoggersRepository.logInfo(() -> this.getClass().getName(), \"ClassLoader {} succesfully created\", classLoader);" +
				"return classLoader;"	
			);
		});
	}
}
