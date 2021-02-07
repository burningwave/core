package org.burningwave.core;

import java.io.Closeable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.junit.jupiter.api.Test;

public class ClassHunterTest extends BaseTest {
	
	@Test
	public void findAllTestOne() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearHuntersCache(false);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/commons-lang")
				)
			).find(),
			(result) ->
				result.getClasses()
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			).find(),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllTestTwo() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> 
				componentSupplier.getClassHunter().findAndCache(),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllUncachedTestFour() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> 
				componentSupplier.getClassHunter().find(),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void getResourceAsStreamTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearHuntersCache(false);
		testNotNull(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			).find(),
			(result) ->{
				Collection<Class<?>> classes = result.getClasses();
				Iterator<Class<?>> itr = classes.iterator();
				Class<?> cls = itr.next();
				while(cls.getClassLoader() == null || !(cls.getClassLoader() instanceof PathScannerClassLoader)) {
					cls = itr.next();
				}
				return ((PathScannerClassLoader)cls.getClassLoader()).getResourceAsStream("META-INF/MANIFEST.MF");
				
			}
		);
	}
	
	@Test
	public void getResourceAsStreamTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearHuntersCache(false);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			).find(),
			(result) -> {
				Collection<Class<?>> classes = result.getClasses();
				Iterator<Class<?>> itr = classes.iterator();
				Class<?> cls = itr.next();
				while(cls.getClassLoader() == null || !(cls.getClassLoader() instanceof PathScannerClassLoader)) {
					cls = itr.next();
				}
				return ((PathScannerClassLoader)cls.getClassLoader()).getResourcesAsStream("META-INF/MANIFEST.MF").values();
				
			}
		);
	}
	
	@Test
	public void refreshCacheTestOne() throws Exception {
		findAllTestOne();
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearHuntersCache(false);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
				)
			).find(),
			(result) ->
				result.getClasses()
		);
		testNotEmpty(
			() -> 
				componentSupplier.getClassHunter().loadInCache(
					SearchConfig.forPaths(
						componentSupplier.getPathHelper().getPath(path -> path.contains("spring-core-4.3.4.RELEASE.jar"))
					).checkForAddedClasses()
			).find(),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllTestThree() throws Exception {
		findAllTestOne();
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
				).checkForAddedClasses()
			).find(),
			(result) ->
				result.getClasses()
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			).find(),
			(result) ->
				result.getClasses()
		);
	}

	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		ClassCriteria classCriteria = ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
			//[1]here you recall the uploaded class by "useClasses" method. In this case we're looking for all classes that extend com.github.burningwave.core.Item
			uploadedClasses.get(Complex.Data.Item.class).isAssignableFrom(currentScannedClass)
		).useClasses(
			//With this directive we ask the library to load one or more classes to be used for comparisons:
			//it serves to eliminate the problem that a class, loaded by different class loaders, 
			//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
			//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
			Complex.Data.Item.class
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					//Search in the runtime Classpaths. Here you can add all absolute path you want:
					//both folders, zip and jar will be scanned recursively
					componentSupplier.getPathHelper().getAllMainClassPaths()
				).by(
					classCriteria
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Serializable.class
					)
				)
			),
			(result) -> result.getClasses()
		);
	}

	
	@Test
	public void findAllSubtypeOfTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						//[1]here you recall the uploaded class by "useClasses" method.
						//In this case we're looking for all classes that implements java.io.Closeable or java.io.Serializable
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						//With this directive we ask the library to load one or more classes to be used for comparisons:
						//it serves to eliminate the problem that a class, loaded by different class loaders, 
						//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
						//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
						Closeable.class,
						Serializable.class
					)
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					//Search in the runtime Classpaths. Here you can add all absolute path you want:
					//both folders, zip and jar will be scanned recursively
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						//[1]here you recall the uploaded class by "useClasses" method. In this case we're looking for all classes that extend java.util.AbstractList
						uploadedClasses.get(AbstractList.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						//With this directive we ask the library to load one or more classes to be used for comparisons:
						//it serves to eliminate the problem that a class, loaded by different class loaders, 
						//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
						//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
						AbstractList.class
					)
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestFiveAndCloseComponentContainer() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).addPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src")
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Serializable.class
					)
				)
			),
			(result) -> result.getClasses()
		);
		closeComponentContainer();
	}
	
	@Test
	public void findAllSubtypeOfTestSix() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).addPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src")
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Serializable.class
					).and(
						ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
							uploadedClasses.get(Comparable.class).isAssignableFrom(currentScannedClass)
						).useClasses(
							Comparable.class
						)
					)
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		ClassHunter classHunter = componentSupplier.getClassHunter();
		testNotEmpty(
			() -> classHunter.findBy(
				SearchConfig.byCriteria(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) -> cls.equals(uploadedClasses.get(Object.class))
						).parameterType(
							(array, idx) -> idx == 0 && array[idx].equals(int.class)
						).skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						)
					).useClasses(
						Closeable.class,
						Serializable.class,
						Object.class
					)
				)
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestEight() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		ClassHunter classHunter = componentSupplier.getClassHunter();
		testNotEmpty(
			() -> classHunter.findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) -> cls.equals(uploadedClasses.get(Object.class))
						).parameterTypesAreAssignableFrom(int.class)
						.skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						)
					).useClasses(
						Closeable.class,
						Serializable.class,
						Object.class
					)
				)
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestNine() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Closeable.class,
						Serializable.class
					)
				).withScanFileCriteria(
					FileSystemItem.Criteria.forClassTypeFiles(
						FileSystemItem.CheckingOption.FOR_NAME).and().allFileThat(fileSystemItem -> 
							fileSystemItem.getAbsolutePath().contains("/org/")
					)
				)
			).find(),
			(result) -> result.getClasses(
				ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
					uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
				).useClasses(
					Serializable.class
				)
			).values()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) ->
								cls.equals(uploadedClasses.get(Object.class))
						).parameterType(
							(array, idx) -> idx == 0 && array[idx].equals(int.class)
						).skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						).result((foundMethods) ->
							foundMethods.size() > 3
						)
					).useClasses(
						Closeable.class,
						Serializable.class,
						Object.class
					)
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths(),
					Arrays.asList(pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip"))
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) ->
								cls.equals(initialClass)
						).parameterType(
							(uploadedClasses, array, idx) ->
								idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
						).skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						)
					).useClasses(
						Closeable.class,
						BigDecimal.class,
						Object.class
					)
				).useDefaultPathScannerClassLoaderAsParent(
					true
				)
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.byScanUpTo(
			(uploadedClasses, initialClass, cls) -> cls.equals(initialClass)
		).parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						methodCriteria
					).useClasses(
						Closeable.class,
						BigDecimal.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				)
			),
			(result) ->
				result.getMembersBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestFive() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.byScanUpTo(
			(uploadedClasses, initialClass, cls) -> cls.equals(initialClass)
		).parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Object.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						methodCriteria
					).useClasses(
						BigDecimal.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				)
			),
			(result) -> result.getMembersBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestSix() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.forEntireClassHierarchy().name(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byMembers(
						methodCriteria
					).useClasses(
						Date.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				)
			),
			(result) -> result.getMembersBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestSeven() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria_01 = MethodCriteria.byScanUpTo(
			(uploadedClasses, initialClass, cls) -> cls.equals(initialClass)
		).parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		MethodCriteria methodCriteria_02 = MethodCriteria.forEntireClassHierarchy().name(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Object.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						methodCriteria_01.or(methodCriteria_02)
					).useClasses(
						BigDecimal.class,
						Date.class,
						Object.class
					)
				)
			),
			(result) -> result.getMembersBy(methodCriteria_01)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithConstructorTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		ConstructorCriteria constructorCriteria = ConstructorCriteria.forEntireClassHierarchy().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths(),
					Arrays.asList(pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip"))
				).by(
					ClassCriteria.create().byMembers(
						constructorCriteria
					).useClasses(
						Date.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				)
			),
			(result) ->
				result.getMembersBy(constructorCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsByAsyncModeTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		
		MethodCriteria methodCriteria = MethodCriteria.forEntireClassHierarchy().name(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byMembers(
						methodCriteria
					).useClasses(
						Date.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				).waitForSearchEnding(
					false
				)	
			),
			(result) -> {
				result.waitForSearchEnding();
				return result.getMembersBy(methodCriteria);
			}
		);
	}
	
	@Test
	public void cacheTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(searchConfig),
			(result) -> result.getClasses()
		);
		searchConfig.by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
			).useClasses(
				Closeable.class
			)
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(searchConfig),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void cacheTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(searchConfig).find(),
			(result) -> result.getClasses()
		);
		searchConfig.by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
			).useClasses(
				Closeable.class
			)
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(searchConfig),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllBurningWaveClassesByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getPath((path) ->
					path.endsWith("target/classes"))
				).by(
					ClassCriteria.create().allThoseThatMatch((currentScannedClass) -> 
						currentScannedClass.getPackage() != null &&
						currentScannedClass.getPackage().getName().startsWith("org.burningwave")
					)
				)
			),
			(result) ->
				result.getClasses(),
			false
		);
	}
	
	@Test
	public void findAllAnnotatedMethods() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
						componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().allThoseThatMatch((cls) -> {
						return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
					}).or().byMembers(
						MethodCriteria.withoutConsideringParentClasses().allThoseThatMatch((method) -> {
							return method.getAnnotations() != null && method.getAnnotations().length > 0;
						})
					)
				)
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllAnnotatedMethodsTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
						componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().allThoseThatMatch((cls) -> {
						return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
					}).or().byMembers(
						MethodCriteria.byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
							return lastClassInHierarchy.equals(currentScannedClass);
						}).allThoseThatMatch((method) -> {
							return method.getAnnotations() != null && method.getAnnotations().length > 0;
						})
					)
				)
			),
			(result) -> result.getMembersBy(MethodCriteria.withoutConsideringParentClasses().allThoseThatMatch((method) -> {
					return method.getAnnotations() != null && method.getAnnotations().length > 0;
				})
			)
		);
	}
	
	@Test
	public void findAllTestOneByIsolatedClassLoader() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
				)
			),
			(result) ->
				result.getClasses()
		);
	}

	@Test
	public void findAllSubtypeOfTestOneByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					//Search in the runtime Classpaths. Here you can add all absolute path you want:
					//both folders, zip and jar will be scanned recursively
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						//[1]here you recall the uploaded class by "useClasses" method. In this case we're looking for all classes that extend com.github.burningwave.core.Item
						uploadedClasses.get(Complex.Data.Item.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						//With this directive we ask the library to load one or more classes to be used for comparisons:
						//it serves to eliminate the problem that a class, loaded by different class loaders, 
						//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
						//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
						Complex.Data.Item.class
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestTwoByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Serializable.class
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses()
		);
	}

	
	@Test
	public void findAllSubtypeOfTestThreeByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						//[1]here you recall the uploaded class by "useClasses" method.
						//In this case we're looking for all classes that implements java.io.Closeable or java.io.Serializable
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						//With this directive we ask the library to load one or more classes to be used for comparisons:
						//it serves to eliminate the problem that a class, loaded by different class loaders, 
						//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
						//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
						Closeable.class,
						Serializable.class
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestFourByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					//Search in the runtime Classpaths. Here you can add all absolute path you want:
					//both folders, zip and jar will be scanned recursively
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						//[1]here you recall the uploaded class by "useClasses" method. In this case we're looking for all classes that extend java.util.AbstractList
						uploadedClasses.get(AbstractList.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						//With this directive we ask the library to load one or more classes to be used for comparisons:
						//it serves to eliminate the problem that a class, loaded by different class loaders, 
						//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
						//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
						AbstractList.class
					)
				).useNewIsolatedClassLoader()
			),
			(result) -> result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestOneByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) -> cls.equals(uploadedClasses.get(Object.class))
						).parameterType(
							(array, idx) -> idx == 0 && array[idx].equals(int.class)
						).skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						)
					).useClasses(
						Closeable.class,
						Serializable.class,
						Object.class
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestTwoByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) ->
								cls.equals(uploadedClasses.get(Object.class))
						).parameterType(
							(array, idx) -> idx == 0 && array[idx].equals(int.class)
						).skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						).result((foundMethods) ->
							foundMethods.size() > 3
						)
					).useClasses(
						Closeable.class,
						Serializable.class,
						Object.class
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestThreeByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths(),
					Arrays.asList(pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip"))
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) -> {
							return uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass);
						}
					).and().byMembers(
						MethodCriteria.byScanUpTo(
							(uploadedClasses, initialClass, cls) ->
								cls.equals(initialClass)
						).parameterType(
							(uploadedClasses, array, idx) ->
								idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
						).skip((classes, initialClass, examinedClass) -> 
							classes.get(Object.class) == examinedClass
						)
					).useClasses(
						Closeable.class,
						BigDecimal.class,
						Object.class
					)
				).useDefaultPathScannerClassLoaderAsParent(
					true
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses(),
			false
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestFourByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.byScanUpTo(
			(uploadedClasses, initialClass, cls) -> cls.equals(initialClass)
		).parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						methodCriteria
					).useClasses(
						Closeable.class,
						BigDecimal.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getMembersBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestFiveByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.byScanUpTo(
			(uploadedClasses, initialClass, cls) -> cls.equals(initialClass)
		).parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Object.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						methodCriteria
					).useClasses(
						BigDecimal.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getMembersBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestSixByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.forEntireClassHierarchy().name(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byMembers(
						methodCriteria
					).useClasses(
						Date.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getMembersBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestSevenByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria_01 = MethodCriteria.byScanUpTo(
			(uploadedClasses, initialClass, cls) -> cls.equals(initialClass)
		).parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(BigDecimal.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		MethodCriteria methodCriteria_02 = MethodCriteria.forEntireClassHierarchy().name(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Object.class).isAssignableFrom(currentScannedClass)
					).and().byMembers(
						methodCriteria_01.or(methodCriteria_02)
					).useClasses(
						BigDecimal.class,
						Date.class,
						Object.class
					)
				)
			),
			(result) -> result.getMembersBy(methodCriteria_01)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithConstructorTestOneByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		ConstructorCriteria constructorCriteria = ConstructorCriteria.withoutConsideringParentClasses().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths(),
					Arrays.asList(pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip"))
				).by(
					ClassCriteria.create().byMembers(
						constructorCriteria
					).useClasses(
						Date.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getMembersBy(constructorCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsByAsyncModeTestOneByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		
		MethodCriteria methodCriteria = MethodCriteria.forEntireClassHierarchy().name(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllPaths()
				).by(
					ClassCriteria.create().byMembers(
						methodCriteria
					).useClasses(
						Date.class,
						Object.class
					)
				).useAsParentClassLoader(
					Thread.currentThread().getContextClassLoader()
				).waitForSearchEnding(
					false
				).useNewIsolatedClassLoader()	
			),
			(result) -> {
				result.waitForSearchEnding();
				return result.getMembersBy(methodCriteria);
			}
		);
	}
	
	@Test
	public void cacheTestOneByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(searchConfig),
			(result) -> result.getClasses()
		);
		searchConfig.by(
			ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
			).useClasses(
				Closeable.class
			)
		).useNewIsolatedClassLoader();
		testNotEmpty(
			() ->
				componentSupplier.getClassHunter().findBy(searchConfig),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void cacheTestTwoByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().loadInCache(searchConfig).find(),
			(result) -> result.getClasses()
		);
		testNotEmpty(() -> 
			componentSupplier.getClassHunter().findBy(
				searchConfig.by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) -> 
						uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Closeable.class
					)
				).useNewIsolatedClassLoader()
			),(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findAllBurningWaveClasses() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearCache(true, true);
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getPath((path) ->
					path.endsWith("target/classes"))
				).by(
					ClassCriteria.create().allThoseThatMatch((currentScannedClass) -> 
						currentScannedClass.getPackage() != null &&
						currentScannedClass.getPackage().getName().startsWith("org.burningwave")
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses(),
			false
		);
	}
	
//	@Test
//	public void findAllWithModuleByIsolatedClassLoader() {
//		ComponentSupplier componentSupplier = getComponentSupplier();
//		testNotEmpty(
//			() -> componentSupplier.getClassHunter().findBy(
//				SearchConfig.forPaths(
//					componentSupplier.getPathHelper().getMainClassPaths()
//				).by(
//					ClassCriteria.create().allThoseThatMatch((currentScannedClass) ->
//						currentScannedClass.getModule().getName() != null && 
//						currentScannedClass.getModule().getName().equals("jdk.xml.dom")
//					)
//				)
//			),
//			(result) ->
//				result.getItemsFound(),
//			false
//		);
//	}
	
	@Test
	public void findAllAnnotatedMethodsByIsolatedClassLoader() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassHunter().findBy(
				SearchConfig.forPaths(
						componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().allThoseThatMatch((cls) -> {
						return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
					}).or().byMembers(
						MethodCriteria.byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
							return lastClassInHierarchy.equals(currentScannedClass);
						}).allThoseThatMatch((method) -> {
							return method.getAnnotations() != null && method.getAnnotations().length > 0;
						})
					)
				).useNewIsolatedClassLoader()
			),
			(result) ->
				result.getClasses()
		);
	}
	
	@Test
	public void findByPackageNameTestOne() {
		testNotEmpty(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
	        PathHelper pathHelper = componentSupplier.getPathHelper();
	        ClassHunter classHunter = componentSupplier.getClassHunter();
	        
	        CacheableSearchConfig searchConfig = SearchConfig.forPaths(
	            pathHelper.getPaths(path -> path.matches(".*?junit-platform-engine-1.7.0.jar"))
	        ).by(
	            ClassCriteria.create().allThoseThatMatch((cls) -> {
	                return cls.getPackage().getName().equals("org.junit.platform.engine");
	            })
	        );
	        try (ClassHunter.SearchResult searchResult = classHunter.loadInCache(searchConfig).find()) {
	            return searchResult.getClasses();
	        }
		}, true);
	}
	
	
	@Test
	public void findByPackageNameTestTwo() {
		testNotEmpty(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
	        PathHelper pathHelper = componentSupplier.getPathHelper();
	        ClassHunter classHunter = componentSupplier.getClassHunter();
	        
	        CacheableSearchConfig searchConfig = SearchConfig.forPaths(
	            pathHelper.loadAndMapPaths("custom", "//${paths.custom-class-path}/ESC-Lib.ear/APP-INF/lib//children:.*?activation-1.1\\.jar;")
	        ).by(
	            ClassCriteria.create().allThoseThatMatch((cls) -> {
	                return cls.getPackage().getName().matches(".*?activation");
	            })
	        );
	        try (ClassHunter.SearchResult searchResult = classHunter.loadInCache(searchConfig).find()) {
	            return searchResult.getClasses();
	        }
		}, true);
	}
}
