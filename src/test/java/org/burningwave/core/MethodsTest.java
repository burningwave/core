package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;


@SuppressWarnings("all")
public class MethodsTest extends BaseTest {
	
	@Test
	public void invokeTestOne() {
		testNotNull(
			() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				componentSupplier.clearHuntersCache(false);
				return Methods.invokeStatic(Integer.class, "valueOf", 1);	
			}
		);
	}
	
	@Test
	public void invokeDirectTestOne() {
		testNotNull(
			() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				componentSupplier.clearHuntersCache(false);
				return Methods.invokeStaticDirect(Integer.class, "valueOf", 1);	
			}
		);
	}
	
	@Test
	public void findAllAndMakeThemAccessibleTestOne() {
		testNotEmpty(
			() -> {
				Methods.findAllAndMakeThemAccessible(System.out.getClass());	
				return Methods.findAllAndMakeThemAccessible(System.out.getClass());	
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
	public void invokeVoidTestThree() {
		testDoesNotThrow(
			() -> {
				Object empty = new Object() {
					void print(String value) {
						System.out.println(value);
					}
				};
				Methods.invoke(empty, "print", null);	
			}
		);
	}
	
	@Test
	public void invokeDirectVoidTestThree() {
		testDoesNotThrow(
			() -> {
				Object empty = new Object() {
					void print(String value) {
						System.out.println(value);
					}
				};
				Methods.invokeDirect(empty, "print", null);	
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
	public void invokeStaticTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeStatic(Service.class, "staticApply", "Hello", "World!", "How are you?");
		});
	}
	
	@Test
	public void invokeStaticWithVarArgsTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeStatic(Service.class, "staticApply", "Hello", "World!", "How are you?", "I'm well");
		});
	}
	
	@Test
	public void invokeStaticDirectWithVarArgsTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeStaticDirect(Service.class, "staticApply", "Hello", "World!", "How are you?", "I'm well");
		});
	}
	
	@Test
	public void invokeDirectStaticTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeStaticDirect(Service.class, "staticApply", "Hello", "World!", "How are you?");
		});
	}
	
	@Test
	public void invokeDirectVoidWithVarArgsTestThree() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "apply", "Hello", "World!", "");
		});
	}
	
	@Test
	public void invokeVoidWithVarArgsTestFour() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "apply", "Hello", "World!", "Hello again", "... And again");
		});
	}
	
	@Test
	public void invokeDirectVoidWithVarArgsTestFour() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "apply", "Hello", "World!", "Hello again", "... And again");
		});
	}
	
	@Test
	public void invokeVoidWithVarArgsTestFive() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invoke(new Service(), "apply", "Hello", "World!", "Hello again");
		});
	}
	
	@Test
	public void invokeDirectVoidWithVarArgsTestFive() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "apply", "Hello", "World!", "Hello again");
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
	
	@Test
	public void invokeDirectMethodWithArrayTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Methods.invokeDirect(new Service(), "withArray", new Object[] {new String[] {"methodWithArray"}});
		});
	}
	
	@Test
	public void findAllTestOne() {
        testNotEmpty(() -> 
	        Methods.findAll(
	            MethodCriteria.byScanUpTo((cls) ->
	            	//We only analyze the ClassLoader class and not all of its hierarchy (default behavior)
	                cls.getName().equals(ClassLoader.class.getName())
	            ).parameter((params, idx) -> {
	                return Classes.isAssignableFrom(params[idx].getType(), Class.class);
	            }), ClassLoader.class
	        ), true
	    );
	}
}
