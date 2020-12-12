package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.service.ExtendedService;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;


public class MembersTest extends BaseTest {
	
	@Test
	public void findOneTestOne() {
		testNotNull(() ->
			Members.findOne(
				MethodCriteria.forEntireClassHierarchy().name((name) ->
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
		testNotNull(() ->
			Members.findOne(
				MethodCriteria.forEntireClassHierarchy()
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
	
	@Test
	public void matchOneTestOne() {
		assertTrue(
			Members.match(
				MethodCriteria.forEntireClassHierarchy()
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
	
	@Test
	public void findAllTestOne() {
		testNotEmpty(() ->
			Members.findAll(
				MethodCriteria.forEntireClassHierarchy().name((name) ->
					name.matches("loadClass")
				),
				PathScannerClassLoader.class
			)
		);
	}
	
	@Test
	public void findFirstTestOne() {
		testNotNull(() ->
			Members.findFirst(
				MethodCriteria.forEntireClassHierarchy().name((name) ->
					name.matches("loadClass")
				),
				PathScannerClassLoader.class
			)
		);
	}
}
