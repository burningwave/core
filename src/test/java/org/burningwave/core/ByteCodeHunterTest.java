package org.burningwave.core;


import java.io.Closeable;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.hunter.ClassFileScanConfiguration;
import org.burningwave.core.classes.hunter.SearchCriteria;
import org.burningwave.core.classes.hunter.SearchForPathCriteria;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

public class ByteCodeHunterTest extends BaseTest {
	
	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchCriteria.forPaths(
					componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
				).byClasses((uploadedClasses, targetClass) ->
					uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
				).deleteFoundItemsOnClose(
					false
				).useClasses(
					Closeable.class
				)
			),
			(result) -> result.getItemsFound()
		);
	}
	
	
	@Test
	public void findAllByNameTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchCriteria.forPaths(
					componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
				).className((iteratedClassName) -> iteratedClassName.startsWith("com"))
			),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void cacheTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		SearchForPathCriteria criteria = SearchCriteria.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		).byClasses((uploadedClasses, targetClass) -> 
			uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
		).useClasses(
			Closeable.class
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(criteria),
			(result) -> result.getItemsFound()
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(criteria),
			(result) -> result.getItemsFound()
		);
	}
	
	
	@Test
	public void uncachedTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		SearchCriteria criteria = SearchCriteria.create().byClasses((uploadedClasses, targetClass) -> 
			uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
		).useClasses(
			Closeable.class
		);
		ClassFileScanConfiguration scanConfig = ClassFileScanConfiguration.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(scanConfig, criteria),
			(result) -> result.getItemsFound()
		);
	}
	
	
	@Test
	public void parallelTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		SearchForPathCriteria criteria = SearchCriteria.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		).byClasses((uploadedClasses, targetClass) -> 
			uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
		).useClasses(
			Closeable.class
		)
		.deleteFoundItemsOnClose(
			false
		);
		Stream.of(
			CompletableFuture.runAsync(() -> componentSupplier.getByteCodeHunter().findBy(
				criteria
			)),
			CompletableFuture.runAsync(() -> componentSupplier.getByteCodeHunter().findBy(
				criteria
			))
		).forEach(cF -> cF.join());
	}
	

	@Test
	public void findAllWithByteCodeEqualsTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchCriteria.forPaths(
					componentSupplier.getPathHelper().getAllClassPaths()
				).byBytecode(
					(byteCodeMap, byteCode) ->
						Arrays.equals(byteCodeMap.get(Service.class), byteCode)
				).useClasses(
					Service.class
				)
			),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllWithByteCodeEqualsAndUseDuplicatedPathsTestOne() {
		findAllSubtypeOfTestOne();
		findAllWithByteCodeEqualsTestOne();
	}
	
	@Test
	public void findAllBurningWaveClasses() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		AtomicReference<Long> bytesWrapper = new AtomicReference<>();
		bytesWrapper.set(0L);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchCriteria.forPaths(
					componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/classes"))
				).allThat((targetClass) -> 
					Object.class.isAssignableFrom(targetClass)
				)
			),
			(result) -> {
				result.getItemsFound().forEach(javaClass -> bytesWrapper.set(bytesWrapper.get() + javaClass.getByteCode().capacity()));
				return result.getItemsFound();
			}
		);
		logDebug("Items total size: " + bytesWrapper.get() + " bytes");
	}
}
