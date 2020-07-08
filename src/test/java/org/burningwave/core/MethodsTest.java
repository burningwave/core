package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;


public class MethodsTest extends BaseTest {
	
	@Test
	public void invokeTestOne() {
		testNotNull(
			() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				componentSupplier.clearHuntersCache(false);
				return Methods.invoke(Integer.class, "valueOf", 1);	
			}
		);
	}
	
	@Test
	public void findAllAndMakeThemAccessibleTestOne() {
		testNotEmpty(
			() -> {
				Methods.findAllAndMakeThemAccessible(System.out);	
				return Methods.findAllAndMakeThemAccessible(System.out);	
			},
		true);
	}
	
	@Test
	public void invokeVoidTestOne() {
		testDoesNotThrow(
			() -> {
				Methods.invoke(System.out, "println", "Hello World");	
			}
		);
	}
	
	@Test
	public void invokeDirectVoidTestTwo() {
		testDoesNotThrow(
			() -> {
				Methods.invokeDirect(System.out, "println", "Hello World");	
			}
		);
	}
	
	@Test
	public void invokeVoidTestTwo() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "apply", "Hello", "World!", new String[]{"How are you?"});
		});
	}
	
	@Test
	public void invokeDirectVoidWithVarArgsTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "apply", "Hello", "World!", new String[]{"How are you?"});
		});
	}
	
	@Test
	public void invokeVoidWithVarArgsTestTwo() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "apply", "Hello", "World!", null);
		});
	}
	
	@Test
	public void invokeDirectVoidWithVarArgsTestTwo() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "apply", "Hello", "World!", null);
		});
	}
	
	@Test
	public void invokeVoidWithVarArgsTestThree() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "apply", "Hello", "World!");
		});
	}
	
	@Test
	public void invokeDirectVoidWithVarArgsTestThree() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "apply", "Hello", "World!");
		});
	}
	
	@Test
	public void invokeNoArgs() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "supply");
		});
	}
	
	@Test
	public void invokeDirectNoArgs() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "supply");
		});
	}
	
	@Test
	public void invokeMethodWithVarArgsTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "methodWithVarArgs");
		});
	}	
	
	@Test
	public void invokeDirectMethodWithVarArgsTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "methodWithVarArgs");
		});
	}
	
	@Test
	public void invokeMethodWithVarArgsTestTwo() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "methodWithVarArgs", "Hello!");
		});
	}
	
	@Test
	public void invokeDirectMethodWithVarArgsTestTwo() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "methodWithVarArgs", "Hello!");
		});
	}
}
