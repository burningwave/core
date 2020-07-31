package org.burningwave.core.examples.member;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;


public class MethodsHandler {
    
    public static void execute() {
        //Invoking method by using reflection
        Methods.invoke(System.out, "println", "Hello World");

        //Invoking static method by using MethodHandle
        Integer number = Methods.invokeStaticDirect(Integer.class, "valueOf", 1);
        
        //Invoking method by using MethodHandle
        Methods.invokeDirect(System.out, "println", number);
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
