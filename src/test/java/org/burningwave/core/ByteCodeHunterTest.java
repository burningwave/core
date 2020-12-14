package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.io.Closeable;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
public class ByteCodeHunterTest extends BaseTest {
	
	
	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
					).useClasses(
						Closeable.class
					)
				).withScanFileCriteria(
					FileSystemItem.Criteria.forClassTypeFiles(
						FileSystemItem.CheckingOption.FOR_NAME
					)
				)					
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
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
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
	public void cacheTestOneTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear")
		).by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) -> 
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
		CacheableSearchConfig searchConfig2 = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear")
		);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(searchConfig2),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void cacheTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
		).by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) -> 
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
		SearchConfig searchConfig = SearchConfig.withoutUsingCache().addPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
		).by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
			).useClasses(
				Closeable.class
			)
		);
		testNotEmpty(
			() ->
				componentSupplier.getByteCodeHunter().findBy(searchConfig),
			(result) ->
				result.getClasses()
		);
	}
	
	
	@Test
	public void parallelTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
		).by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
			).useClasses(
				Closeable.class
			)
		);
		Stream.of(
			CompletableFuture.runAsync(() -> 
				componentSupplier.getByteCodeHunter().findBy(
					searchConfig
				)
			),
			CompletableFuture.runAsync(() ->
				componentSupplier.getByteCodeHunter().findBy(
					searchConfig
				)
			)
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
//		Collection<String> disabledLoggers = GlobalProperties.resolveStringValues("managed-logger.repository.logging.warn.disabled-for", ";");
//		disabledLoggers.remove(PathScannerClassLoader.class.getName());
//		GlobalProperties.put("managed-logger.repository.logging.warn.disabled-for", String.join(";", disabledLoggers));
		findAllSubtypeOfTestOne();
		findAllWithByteCodeEqualsTestOne();
//		disabledLoggers.add(PathScannerClassLoader.class.getName());
//		GlobalProperties.put("managed-logger.repository.logging.warn.disabled-for", String.join(";", disabledLoggers));
	}
	
	@Test
	public void findAllBurningWaveClassesTest() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		AtomicReference<Long> bytesWrapper = new AtomicReference<>();
		bytesWrapper.set(0L);
		testNotEmpty(
			() -> componentSupplier.getByteCodeHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/classes"))
				).by(
					ClassCriteria.create().allThoseThatMatch((targetClass) -> 
						Object.class.isAssignableFrom(targetClass)
					)
				)
			),
			(result) -> {
				result.getClasses().forEach(javaClass -> bytesWrapper.set(bytesWrapper.get() + javaClass.getByteCode().capacity()));
				return result.getClasses();
			}
		);
		ManagedLoggersRepository.logDebug(getClass()::getName, "Items total size: " + bytesWrapper.get() + " bytes");
	}
}
