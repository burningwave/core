package org.burningwave.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Logger;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingSupplier;

public class BaseTest implements Logger {

	Collection<ComponentSupplier> componentSuppliers = new CopyOnWriteArrayList<>();
	
	protected ComponentSupplier getComponentSupplier() {
		//Set<ComponentSupplier> componentSuppliers = getComponentSupplierSetForTest();
		//return getNewComponentSupplier();
		return ComponentSupplier.getInstance();
	}

	protected Set<ComponentSupplier> getComponentSupplierSetForTest() {
		Set<ComponentSupplier> componentSuppliers = ConcurrentHashMap.newKeySet();
		List<Thread> threadList = new CopyOnWriteArrayList<>();
		for (int i = 0; i < 1000; i++) {
			Thread thread = new Thread() {
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
		
	void testNotNull(ThrowingSupplier<?> supplier) {
		Object object = null;
		try {
			object = supplier.get();
		} catch (Throwable exc) {
			logError("Exception occurred", exc);
		}
		assertNotNull(object);
	}
	
	void testNotEmpty(ThrowingSupplier<Collection<?>> supplier) {
		long initialTime = System.currentTimeMillis();
		Collection<?> coll = null;
		try {
			coll = supplier.get();
			logInfo("Found " + coll.size() + " items in " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
		} catch (Throwable exc) {
			logError("Exception occurred", exc);
		}
		assertTrue(!coll.isEmpty());
	}
	
	<T extends AutoCloseable> void testNotEmpty(Supplier<T> autoCloaseableSupplier, Function<T, Collection<?>> collSupplier) {
		long initialTime = System.currentTimeMillis();
		Collection<?> coll = null;
		boolean isNotEmpty = false;
		try (T collectionSupplier = autoCloaseableSupplier.get()){
			coll = collSupplier.apply(collectionSupplier);
			logInfo("Found " + coll.size() + " items in " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
			isNotEmpty = !coll.isEmpty();
		} catch (Throwable exc) {
			logError("Exception occurred", exc);
		}
		assertTrue(isNotEmpty);
	}
	
	
	<T extends AutoCloseable> void testNotNull(
		ThrowingSupplier<T> autoCloseableSupplier, 
		Function<T, ?> objectSupplier
	) {
		long initialTime = System.currentTimeMillis();
		try (T autoCloseable = autoCloseableSupplier.get()) {
			assertNotNull(objectSupplier.apply(autoCloseable));
			logInfo("Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
		} catch (Throwable exc) {
			logError("Exception occurred", exc);
		}		
	}
	
	
	void testDoesNotThrow(Executable executable) {
		Throwable throwable = null;
		long initialTime = System.currentTimeMillis();
		try {
			executable.execute();
			logInfo("Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(), initialTime));
		} catch (Throwable exc) {
			logError("Exception occurred", exc);
			throwable = exc;
		}
		assertNull(throwable);
	}
	
	
	private String getFormattedDifferenceOfMillis(long value1, long value2) {
		String valueFormatted = String.format("%04d", (value1 - value2));
		return valueFormatted.substring(0, valueFormatted.length() - 3) + "," + valueFormatted.substring(valueFormatted.length() -3);
	}
	
	
	@Override
	protected void finalize() throws Throwable {
		componentSuppliers.forEach(componentSupplier -> componentSupplier.close());
		componentSuppliers.clear();
	}

}
