package org.burningwave.core.examples.functionalinterfacefactory;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.util.UUID;

import org.burningwave.core.ManagedLogger;

@SuppressWarnings("unused")
public class Service implements ManagedLogger {
    
    private String id;
    private String name;
    private String[] items;
    
    //Constructors
    private Service(String id, String name, String... items) {
        this.id = id;
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter constructor:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
    }
    
    private Service(String name, String... items) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter constructor:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
    }
    
    private Service(String... name) {
        this.id = UUID.randomUUID().toString();
        this.name = name[0];
        this.items = null;
        logInfo("\nSingle parameter varargs constructor:\n\tname: {}", this.name);
    }
    
    private Service(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = null;
        logInfo("\nSingle parameter constructor:\n\tname: {}", this.name);
    }

    private Service() {
        this.id = UUID.randomUUID().toString();
        this.name = "no name";
        this.items = null;
        logInfo("\nNo parameter constructor:\n\tname: {}", this.name);
    }

    // Methods
    private Long reset(String id, String name, String... items) {
        this.id = id;
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter method:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
        return System.currentTimeMillis();
    }
    
    private Long reset(String name, String... items) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter method:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
        return System.currentTimeMillis();
    }
    
    private Long reset(String... name) {
        this.id = UUID.randomUUID().toString();
        this.name = name[0];
        this.items = null;
        logInfo("\nSingle parameter varargs method:\n\tname: {}", this.name);
        return System.currentTimeMillis();
    }
    
    private Long reset(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = null;
        logInfo("\nSingle parameter method:\n\tname: {}", this.name);
        return System.currentTimeMillis();
    }

    private Long reset() {
        this.id = UUID.randomUUID().toString();
        this.name = "no name";
        this.items = null;
        logInfo("\nNo parameter method:\n\tname: {}", this.name);
        return System.currentTimeMillis();
    }
    
    private void voidReset(String id, String name, String... items) {
        this.id = id;
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter void method:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
    }
    
    private void voidReset(String name, String... items) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter void method:\n\tname: {} \n\titems: {}", this.name, String.join(", ", this.items));
    }
    
    private void voidReset(String... name) {
        this.id = UUID.randomUUID().toString();
        this.name = name[0];
        this.items = null;
        logInfo("\nSingle parameter void varargs method:\n\tname: {}", this.name);
    }
    
    private void voidReset(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = "no name";
        this.items = null;
        logInfo("\nSingle parameter void method:\n\tname: {}", this.name);
    }

    private void voidReset() {
        this.id = UUID.randomUUID().toString();
        this.name = "no name";
        this.items = null;
        logInfo("\nNo parameter void method:\n\tname: {}", this.name);
    }
    
    private boolean resetWithBooleanReturn(String id, String name, String... items) {
        this.id = id;
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter method with boolean return:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
        return true;
    }
    
    private boolean resetWithBooleanReturn(String name, String... items) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = items;
        logInfo("\nMultiparameter method with boolean return:\n\tid: {}\n\tname: {} \n\titems: {}", this.id, this.name, String.join(", ", this.items));
        return true;
    }
    
    private boolean resetWithBooleanReturn(String... name) {
        this.id = UUID.randomUUID().toString();
        this.name = name[0];
        this.items = null;
        logInfo("\nSingle parameter varargs method with boolean return:\n\tname: {}", this.name);
        return true;
    }
    
    private boolean resetWithBooleanReturn(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.items = null;
        logInfo("\nSingle parameter method with boolean return:\n\tname: {}", this.name);
        return true;
    }
    
    private boolean resetWithBooleanReturn() {
        this.id = UUID.randomUUID().toString();
        this.name = "no name";
        this.items = null;
        logInfo("\nNo parameter method with boolean return:\n\tname: {}", this.name);
        return true;
    }
    
    // Static methods
    private static Long staticDoSomething(String id, String name, String... items) {
    	ManagedLoggersRepository.logInfo(() -> Service.class.getName(), "\nMultiparameter static method:\n\tid: {}\n\tname: {} \n\titems: {}", id, name, String.join(", ", items));
        return System.currentTimeMillis();
    }
    
    private static Long staticDoSomething(String name, String... items) {
        ManagedLoggersRepository.logInfo(
        	() -> Service.class.getName(),
        	"\nMultiparameter static method:\n\tid: {}\n\tname: {} \n\titems: {}",
        	UUID.randomUUID().toString(), name, String.join(", ", items)
        );
        return System.currentTimeMillis();
    }
    
    private static Long staticDoSomething(String... name) {
    	 ManagedLoggersRepository.logInfo(
    	     () -> Service.class.getName(), "\nSingle parameter static varargs method:\n\tname: {}", name[0]
    	 );
        return System.currentTimeMillis();
    }
    
    private static Long staticDoSomething(String name) {
   	    ManagedLoggersRepository.logInfo(
    	    () -> Service.class.getName(), "\nSingle parameter static method:\n\tname: {}", name
    	);
        return System.currentTimeMillis();
    }

    private static Long staticDoSomething() {
   	    ManagedLoggersRepository.logInfo(
   	    	() -> Service.class.getName(), "\nNo parameter static  method:\n\tname: {}", "no name"
   	    );
        return System.currentTimeMillis();
    }
    
    private static void staticVoidDoSomething(String id, String name, String... items) {
    	ManagedLoggersRepository.logInfo(() -> Service.class.getName(), "\nMultiparameter static void method:\n\tid: {}\n\tname: {} \n\titems: {}", id, name, String.join(", ", items));
    }
    
    private static void staticVoidDoSomething(String name, String... items) {
        ManagedLoggersRepository.logInfo(
        	() -> Service.class.getName(),
        	"\nMultiparameter static void method:\n\tid: {}\n\tname: {} \n\titems: {}",
        	UUID.randomUUID().toString(), name, String.join(", ", items)
        );
    }
    
    private static void staticVoidDoSomething(String... name) {
    	 ManagedLoggersRepository.logInfo(
    	     () -> Service.class.getName(), "\nSingle parameter static void varargs method:\n\tname: {}", name[0]
    	 );
    }
    
    private static void staticVoidDoSomething(String name) {
   	    ManagedLoggersRepository.logInfo(
    	    () -> Service.class.getName(), "\nSingle parameter static void method:\n\tname: {}", name
    	);
    }

    private static void staticVoidDoSomething() {
   	    ManagedLoggersRepository.logInfo(
   	    	() -> Service.class.getName(), "\nNo parameter static  void method:\n\tname: {}", "no name"
   	    );
    }
    
    private static boolean staticDoSomethingWithBooleanReturn(String id, String name, String... items) {
    	ManagedLoggersRepository.logInfo(() -> Service.class.getName(), "\nMultiparameter static method:\n\tid: {}\n\tname: {} \n\titems: {}", id, name, String.join(", ", items));
        return true;
    }
    
    private static boolean staticDoSomethingWithBooleanReturn(String name, String... items) {
        ManagedLoggersRepository.logInfo(
        	() -> Service.class.getName(),
        	"\nMultiparameter static method:\n\tid: {}\n\tname: {} \n\titems: {}",
        	UUID.randomUUID().toString(), name, String.join(", ", items)
        );
        return true;
    }
    
    private static boolean staticDoSomethingWithBooleanReturn(String... name) {
    	 ManagedLoggersRepository.logInfo(
    	     () -> Service.class.getName(), "\nSingle parameter static varargs method:\n\tname: {}", name[0]
    	 );
    	 return true;
    }
    
    private static boolean staticDoSomethingWithBooleanReturn(String name) {
   	    ManagedLoggersRepository.logInfo(
    	    () -> Service.class.getName(), "\nSingle parameter static method:\n\tname: {}", name
    	);
   	    return true;
    }

    private static boolean staticDoSomethingWithBooleanReturn() {
   	    ManagedLoggersRepository.logInfo(
   	    	() -> Service.class.getName(), "\nNo parameter static  method:\n\tname: {}", "no name"
   	    );
   	    return true;
    }
    
}
