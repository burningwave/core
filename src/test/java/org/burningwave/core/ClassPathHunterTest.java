package org.burningwave.core;

import java.io.Closeable;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.classes.hunter.SearchCriteria;
import org.burningwave.core.classes.hunter.SearchForPathCriteria;
import org.junit.jupiter.api.Test;

public class ClassPathHunterTest extends BaseTest {
	
	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(
				SearchCriteria.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).byClasses((uploadedClasses, targetClass) ->
					uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
				).useClasses(
					Closeable.class
				)
			),
			(result) -> result.getItemsFound()
		);
	}

	@Test
	public void findAllSubtypeOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(
				SearchCriteria.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).byClasses((uploadedClasses, targetClass) ->
					uploadedClasses.get(Complex.Data.Item.class).isAssignableFrom(targetClass)
				).useClasses(
					Complex.Data.Item.class
				)
			),
			(result) -> result.getItemsFound()
		);
	}
	
	@Test
	public void cacheTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		SearchForPathCriteria criteria = SearchCriteria.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(criteria),
			(result) -> result.getItemsFound()
		);
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(
				criteria.byClasses((uploadedClasses, targetClass) ->
					uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
				).useClasses(
					Closeable.class
				)
			),
			(result) -> result.getItemsFound()
		);
	}

}
