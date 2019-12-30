package org.burningwave.core;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

public class FunctionalInterfaceFactoryTest extends BaseTest {
	
	@Test
	public void getOrBuildFunctionClassTestOne() throws Throwable {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testDoesNotThrow(() -> {
			Method mth = componentSupplier.getMemberFinder().findOne(
				MethodCriteria.create()
				.name((name) -> name.matches("apply"))
				.and().parameterType((params, idx) -> idx == 0 && params[idx].equals(Object.class))
				.and().parameterType((params, idx) -> idx == 1 && params[idx].equals(String.class))
				.and().parameterType((params, idx) -> idx == 2 && params[idx].equals(String.class)),
				Service.class				
			);
			Virtual virtualObj = componentSupplier.getFunctionalInterfaceFactory().create(new Service(), mth);
			virtualObj.invoke(
				"apply",
				"Hello", "World!", "How are you?"
			);
		});
	}
	
	@Test
	public void getOrBuildFunctionClassTestTwo() throws Throwable {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testDoesNotThrow(() -> {
			Method mth = componentSupplier.getMemberFinder().findOne(
				MethodCriteria.create()
				.name((name) -> name.matches("apply"))
				.and().parameterType((params, idx) -> idx == 0 && params[idx].equals(String.class))
				.and().parameterTypes((params) -> params.length == 1),
				Service.class
			);
			Function<String, String> bindedFunction = componentSupplier.getFunctionalInterfaceFactory().create(new Service(), mth);
			bindedFunction.apply(
				"Hello World!"
			);
		});
	}
}
