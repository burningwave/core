package org.burningwave.core;


import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.service.ExtendedService;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;


public class MemberFinderTest extends BaseTest {
	
	@Test
	public void findOneTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() ->
			componentSupplier.getMemberFinder().findOne(
				MethodCriteria.forName((name) ->
					name.matches("apply")
				).and().parameterType((params, idx) -> 
					idx == 0 && params[idx].equals(Object.class)
				).and().parameterType((params, idx) -> 
					idx == 1 && params[idx].equals(String.class)
				).and().parameterType((params, idx) ->
					idx == 2 && params[idx].equals(String.class)
				),
				Service.class
			)
		);
	}
	
	
	@Test
	public void findOneTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() ->
			componentSupplier.getMemberFinder().findOne(
				MethodCriteria.create()
				.name((name) -> name.matches("apply"))
				.and().parameterType((params, idx) -> 
					idx == 0 && params[idx].equals(Object.class)
				)
				.and().parameterType((params, idx) -> 
					idx == 1 && params[idx].equals(String.class)
				)
				.and().parameterType((params, idx) ->
					idx == 2 && params[idx].equals(String.class)
				).skip((initialClass, cls) ->
					Object.class == cls || Service.class == cls
				),
				ExtendedService.class
			)
		);
	}	
}
