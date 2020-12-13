package org.burningwave.core;

import java.io.Closeable;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.SearchConfig;
import org.junit.jupiter.api.Test;

public class ClassPathHunterTest extends BaseTest {
	
	@Test
	public void findAllSubtypeOfTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
					).useClasses(
						Closeable.class
					)
				)
			),
			(result) ->
				result.getClassPaths(),
			true
		);
	}

	@Test
	public void findAllSubtypeOfTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getMainClassPaths()
				).by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) ->
						uploadedClasses.get(Complex.Data.Item.class).isAssignableFrom(targetClass)
					).useClasses(
						Complex.Data.Item.class
					)
				)
			),
			(result) ->
				result.getClassPaths()
		);
	}
	
	@Test
	public void cacheTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			componentSupplier.getPathHelper().getMainClassPaths()
		);
		testNotEmpty(
			() ->
				componentSupplier.getClassPathHunter().findBy(searchConfig),
			(result) ->
				result.getClassPaths()
		);
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().findBy(
				searchConfig.by(
					ClassCriteria.create().byClassesThatMatch((uploadedClasses, targetClass) ->
						uploadedClasses.get(Closeable.class).isAssignableFrom(targetClass)
					).useClasses(
						Closeable.class
					)
				)
			),
			(result) ->
				result.getClassPaths(),
			true
		);
	}
	
	@Test
	public void findAllTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		componentSupplier.clearHuntersCache(false);
		testNotEmpty(
			() -> componentSupplier.getClassPathHunter().loadInCache(
				SearchConfig.forPaths(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
				)
			).find(),
			(result) -> {
				return result.getClassPaths();				
			}, true
		);
	}

}
