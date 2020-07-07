package org.burningwave.core.examples.member;

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;

import java.util.Collection;
import java.util.Map;


@SuppressWarnings("unused")
public class FieldsHandler {
    
    public static void execute() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //Fast access by memory address
        Collection<Class<?>> loadedClasses = Fields.getDirect(classLoader, "classes");
        //Access by Reflection
        loadedClasses = Fields.get(classLoader, "classes");
        
        //Get all fields of an object through memory address access
        Map<String, Object> values = Fields.getAllDirect(classLoader);
        //Get all fields of an object through reflection access
        values = Fields.getAll(classLoader);
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
