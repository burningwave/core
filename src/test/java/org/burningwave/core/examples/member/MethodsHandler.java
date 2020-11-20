package org.burningwave.core.examples.member;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;

import org.burningwave.core.classes.MethodCriteria;


@SuppressWarnings("unused")
public class MethodsHandler {
    
    public static void execute() {
        //Invoking method by using reflection
        Methods.invoke(System.out, "println", "Hello World");

        //Invoking static method by using MethodHandle
        Integer number = Methods.invokeStaticDirect(Integer.class, "valueOf", 1);
        
        //Invoking method by using MethodHandle
        Methods.invokeDirect(System.out, "println", number);
        
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		
        //Filtering and obtaining a MethodHandle reference
        Methods.findFirstDirectHandle(
        	MethodCriteria.byScanUpTo((cls) -> 
				cls.getName().equals(ClassLoader.class.getName())
			).name(
				"defineClass"::equals
			).and().parameterTypes(params -> 
				params.length == 3
			).and().parameterTypesAreAssignableFrom(
				String.class, ByteBuffer.class, ProtectionDomain.class
			).and().returnType((cls) -> 
				cls.getName().equals(Class.class.getName())
			), classLoader.getClass()
        );

    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
