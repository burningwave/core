package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingSupplier;

@SuppressWarnings("unused")
//@TestMethodOrder(Random.class)
//@TestMethodOrder(MethodName.class)
public class BaseTest implements Component {

	Collection<ComponentSupplier> componentSuppliers = new CopyOnWriteArrayList<>();
	private static ComponentContainer componentSupplier;
	
//	protected ComponentSupplier getComponentSupplier() {
//		//Set<ComponentSupplier> componentSuppliers = getComponentSupplierSetForTest();
//		//return getNewComponentSupplier();
//		return ComponentSupplier.getInstance();
//	}
	
	protected synchronized ComponentContainer getComponentSupplier() {
		if (componentSupplier == null || componentSupplier.isClosed()) {
			return componentSupplier = ComponentContainer.create("burningwave.properties");
		}
		return componentSupplier;
		//return ComponentSupplier.getInstance();
	}
	
	
	public synchronized void closeComponentContainer() {
		componentSupplier.close();
		componentSupplier = null;
	}

	protected Set<ComponentSupplier> getComponentSupplierSetForTest() {
		Set<ComponentSupplier> componentSuppliers = ConcurrentHashMap.newKeySet();
		List<Thread> threadList = new CopyOnWriteArrayList<>();
		for (int i = 0; i < 1000; i++) {
			Thread thread = new Thread() {
				@Override
				public void run() {
					componentSuppliers.add(ComponentSupplier.getInstance());
				};
			};
			threadList.add(thread);
			thread.start();
		}
		threadList.forEach(thr -> {
			try {
				thr.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		return componentSuppliers;
	}
	
	protected ComponentSupplier getNewComponentSupplier() {
		ComponentSupplier componentSupplier = ComponentContainer.create("burningwave.properties");
		componentSuppliers.add(componentSupplier);
		return componentSupplier;
	}
		
	public void testNotNull(ThrowingSupplier<?> supplier) {
		Object object = null;
		try {
			ManagedLoggersRepository.logInfo(getClass()::getName, getCallerMethod() + " - start execution");
			object = supplier.get();
			ManagedLoggersRepository.logInfo(getClass()::getName, getCallerMethod() + " - end execution");
		} catch (Throwable exc) {
			ManagedLoggersRepository.logError(getClass()::getName, getCallerMethod() + " - Exception occurred", exc);
		}
		assertNotNull(object);
	}
	
	protected void testNotEmpty(ThrowingSupplier<Collection<?>> supplier) {
		testNotEmpty(supplier, false);
	}
	
	protected void testNotEmpty(ThrowingSupplier<Collection<?>> supplier, boolean printAllElements) {
		long initialTime = System.currentTimeMillis();
		Collection<?> coll = null;
		boolean isNotEmpty = false;
		try {
			coll = supplier.get();
			ManagedLoggersRepository.logInfo(getClass()::getName, getCallerMethod() + " - Found " + coll.size() + " items in " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
			isNotEmpty = !coll.isEmpty();
			if (isNotEmpty && printAllElements) {
				for (Object obj : coll) {
					if (obj != null) {
						ManagedLoggersRepository.logDebug(getClass()::getName, "{}", obj);
					}
				}
			}
		} catch (Throwable exc) {
			ManagedLoggersRepository.logError(getClass()::getName, getCallerMethod() + " - Exception occurred", exc);
		}
		assertTrue(!coll.isEmpty());
	}
	
	private String getCallerMethod() {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		for (StackTraceElement stackTraceElement : stackTraceElements) {
			String className = stackTraceElement.getClassName();
			if (className.contains("Test") && !className.contains("BaseTest")) {
				return stackTraceElement.getMethodName();
			}
		}
		return null;
	}

	public <T extends AutoCloseable> void testNotEmpty(Supplier<T> autoCloaseableSupplier, Function<T, Collection<?>> collSupplier) {
		testNotEmpty(autoCloaseableSupplier, collSupplier, false);
	}
	
	public <T extends AutoCloseable> void testNotEmpty(Supplier<T> autoCloaseableSupplier, Function<T, Collection<?>> collSupplier, boolean printAllElements) {
		long initialTime = System.currentTimeMillis();
		Collection<?> coll = null;
		boolean isNotEmpty = false;
		try (T collectionSupplier = autoCloaseableSupplier.get()){
			coll = collSupplier.apply(collectionSupplier);
			ManagedLoggersRepository.logInfo(getClass()::getName, getCallerMethod() + " - Found " + coll.size() + " items in " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
			isNotEmpty = !coll.isEmpty();
			if (isNotEmpty && printAllElements) {
				coll.forEach(element -> ManagedLoggersRepository.logDebug(getClass()::getName, 
					Optional.ofNullable(element.toString()).orElseGet(() -> null)
				));
			}
		} catch (Throwable exc) {
			ManagedLoggersRepository.logError(getClass()::getName, getCallerMethod() + " - Exception occurred", exc);
		}
		assertTrue(isNotEmpty);
	}
	
	
	public <T extends AutoCloseable> void testNotNull(
		ThrowingSupplier<T> autoCloseableSupplier, 
		Function<T, ?> objectSupplier
	) {
		long initialTime = System.currentTimeMillis();
		try (T autoCloseable = autoCloseableSupplier.get()) {
			assertNotNull(objectSupplier.apply(autoCloseable));
			ManagedLoggersRepository.logInfo(getClass()::getName, getCallerMethod() + " - Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
		} catch (Throwable exc) {
			ManagedLoggersRepository.logError(getClass()::getName, getCallerMethod() + " - Exception occurred", exc);
		}		
	}
	
	
	public void testDoesNotThrow(Executable executable) {
		Throwable throwable = null;
		long initialTime = System.currentTimeMillis();
		try {
			ManagedLoggersRepository.logDebug(getClass()::getName, getCallerMethod() + " - Initializing logger");
			executable.execute();
			ManagedLoggersRepository.logInfo(getClass()::getName, getCallerMethod() + " - Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
		} catch (Throwable exc) {
			ManagedLoggersRepository.logError(getClass()::getName, getCallerMethod() + " - Exception occurred", exc);
			throwable = exc;
		}
		assertNull(throwable);
	}
	
	
	String getFormattedDifferenceOfMillis(long value1, long value2) {
		String valueFormatted = String.format("%04d", (value1 - value2));
		return valueFormatted.substring(0, valueFormatted.length() - 3) + "," + valueFormatted.substring(valueFormatted.length() -3);
	}
	
	void waitFor(long seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException exc) {
			Throwables.throwException(exc);
		}
	}
	
}