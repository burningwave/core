package org.burningwave.core.examples.member;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;


@SuppressWarnings("unused")
public class MethodsHandler {
    
    public static void execute() {
        //Inoking method by using reflection
        Methods.invoke(System.out, "println", "Hello World");
        
        //Inoking method by using MethodHandle
        Integer number = Methods.invokeDirect(Integer.class, "valueOf", 1);
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
