package org.burningwave.core.examples.functionalinterfacefactory;

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
    
}
