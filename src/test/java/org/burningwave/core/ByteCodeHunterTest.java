package org.burningwave.core;


import java.io.Closeable;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.ClassFileScanConfig;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

public class ByteCodeHunterTest extends BaseTest {
	
	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, targetClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
					).useClasses(
						Closeable.class
					)
				).deleteFoundItemsOnClose(
					false
				).deepFileCheck(false)					
			),
			(result) -> result.getClasses()
		);
	}
	
	
	@Test
	public void findAllByNameTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
				).by(
					ClassCriteria.create().className((iteratedClassName) ->
						iteratedClassName.startsWith("com")
					)
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void cacheTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		).by(
			ClassCriteria.create().byClasses((uploadedClasses, targetClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
			).useClasses(
				Closeable.class
			)
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(searchConfig),
			(result) -> result.getClasses()
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(searchConfig),
			(result) -> result.getClasses()
		);
	}
	
	
	@Test
	public void uncachedTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		SearchConfig searchConfig = SearchConfig.create().by(
			ClassCriteria.create().byClasses((uploadedClasses, targetClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
			).useClasses(
				Closeable.class
			)
		);
		ClassFileScanConfig scanConfig = ClassFileScanConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(scanConfig, searchConfig),
			(result) -> result.getClasses()
		);
	}
	
	
	@Test
	public void parallelTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		).by(
			ClassCriteria.create().byClasses((uploadedClasses, targetClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
			).useClasses(
				Closeable.class
			)
		).deleteFoundItemsOnClose(
			false
		);
		Stream.of(
			CompletableFuture.runAsync(() -> componentSupplier.getByteCodeHunter().findBy(
				searchConfig
			)),
			CompletableFuture.runAsync(() -> componentSupplier.getByteCodeHunter().findBy(
				searchConfig
			))
		).forEach(cF -> cF.join());
	}
	

	@Test
	public void findAllWithByteCodeEqualsTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byBytecode((byteCodeMap, byteCode) ->
						Arrays.equals(byteCodeMap.get(Service.class), byteCode)
					).useClasses(
						Service.class
					)
				)
			),
			(result) -> result.getClasses()
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
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/classes"))
				).by(
					ClassCriteria.create().allThat((targetClass) -> 
						Object.class.isAssignableFrom(targetClass)
					)
				)
			),
			(result) -> {
				result.getClasses().forEach(javaClass -> bytesWrapper.set(bytesWrapper.get() + javaClass.getByteCode().capacity()));
				return result.getClasses();
			}
		);
		logDebug("Items total size: " + bytesWrapper.get() + " bytes");
	}
}
