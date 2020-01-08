package org.burningwave.core;

import java.io.Closeable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.Date;

import org.burningwave.core.Item;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.classes.hunter.CacheableSearchConfig;
import org.junit.jupiter.api.Test;

public class FSIClassHunterTest extends BaseTest {
	
	@Test
	public void findAllTestOne() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
				)
			),
			(result) ->
				result.getItemsFound()
		);
	}

	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					//Search in the runtime Classpaths. Here you can add all absolute path you want:
					//both folders, zip and jar will be scanned recursively
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
						//[1]here you recall the uploaded class by "useClasses" method. In this case we're looking for all classes that extend org.burningwave.core.Item
						uploadedClasses.get(Item.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						//With this directive we ask the library to load one or more classes to be used for comparisons:
						//it serves to eliminate the problem that a class, loaded by different class loaders, 
						//turns out to be different for the comparison operators (eg. The isAssignableFrom method).
						//If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
						Item.class
					)
				)
			),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
						uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
					).useClasses(
						Serializable.class
					)
				)
			),
			(result) -> result.getItemsFound()
		);
	}

	
	@Test
	public void findAllSubtypeOfTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllSubtypeOfTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					//Search in the runtime Classpaths. Here you can add all absolute path you want:
					//both folders, zip and jar will be scanned recursively
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
				).useSharedClassLoaderAsParent(
					true
				)
			),
			(result) ->
				result.getItemsFound()
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
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
				result.getMembersFoundBy(methodCriteria)
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
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
			(result) -> result.getMembersFoundBy(methodCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsTestSix() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		MethodCriteria methodCriteria = MethodCriteria.forName(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
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
			(result) -> result.getMembersFoundBy(methodCriteria)
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
		
		MethodCriteria methodCriteria_02 = MethodCriteria.forName(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllClassPaths()
				).by(
					ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) ->
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
			(result) -> result.getMembersFoundBy(methodCriteria_01)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithConstructorTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		ConstructorCriteria constructorCriteria = ConstructorCriteria.create().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
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
				result.getMembersFoundBy(constructorCriteria)
		);
	}
	
	@Test
	public void findAllSubtypeOfWithMethodsByAsyncModeTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		 MethodCriteria methodCriteria = MethodCriteria.forName(
			(methodName) -> methodName.startsWith("set")
		).and().parameterType(
			(uploadedClasses, array, idx) ->
				idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
		).skip((classes, initialClass, examinedClass) -> 
			classes.get(Object.class) == examinedClass
		);
		
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAllClassPaths()
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
				return result.getMembersFoundBy(methodCriteria);
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
			() -> componentSupplier.getFSIClassHunter().findBy(searchConfig),
			(result) -> result.getItemsFound()
		);
		searchConfig.by(
			ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
			).useClasses(
				Closeable.class
			)
		);
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(searchConfig),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void cacheTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		componentSupplier.getFSIClassHunter().loadCache(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(searchConfig),
			(result) -> result.getItemsFound()
		);
		searchConfig.by(
			ClassCriteria.create().byClasses((uploadedClasses, currentScannedClass) -> 
				uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass)
			).useClasses(
				Closeable.class
			)
		);
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(searchConfig),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllBurningWaveClasses() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getClassPath((path) ->
					path.endsWith("target/classes"))
				).by(
					ClassCriteria.create().allThat((currentScannedClass) -> 
						currentScannedClass.getPackage() != null &&
						currentScannedClass.getPackage().getName().startsWith("org.burningwave")
					)
				)
			),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void findAllAnnotatedMethods() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getFSIClassHunter().findBy(
				SearchConfig.forPaths(
						componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().allThat((cls) -> {
						return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
					}).or().byMembers(
						MethodCriteria.byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
							return lastClassInHierarchy.equals(currentScannedClass);
						}).allThat((method) -> {
							return method.getAnnotations() != null && method.getAnnotations().length > 0;
						})
					)
				)
			),
			(result) -> result.getItemsFound()
		);
	}
		
}
