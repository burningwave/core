package org.burningwave.core.examples.functionalinterfacefactory;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;

@SuppressWarnings("unused")
public class Binder {
    
    
    private Service testConstructorsBinding() {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsFunction<Service> serviceInstantiatorZero = fIF.getOrCreate(Service.class, String.class, String.class, String[].class);
        Service serviceZero = serviceInstantiatorZero.apply(UUID.randomUUID().toString(), "Service Zero", new String[] {"item 1", "item 2"});
        
        BiFunction<String, String[], Service> serviceInstantiatorOne = fIF.getOrCreate(Service.class, String.class, String[].class);
		Service serviceOne = serviceInstantiatorOne.apply("Service One", new String[] {"item 1", "item 2"});
        
        Function<String[], Service> serviceInstantiatorTwo = fIF.getOrCreate(Service.class, String[].class);
        Service serviceTwo = serviceInstantiatorTwo.apply(new String[] {"Service Two"});
        
        Function<String, Service> serviceInstantiatorThree = fIF.getOrCreate(Service.class, String.class);
        Service serviceThree = serviceInstantiatorThree.apply("Service Three");
        
        Supplier<Service> serviceInstantiatorFour = fIF.getOrCreate(Service.class);
        Service serviceFour = serviceInstantiatorFour.get();
        
        return serviceZero;
    }
    
    private void testMethodsBinding(Service service) {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsFunction<Long> methodInvokerZero = fIF.getOrCreate(Service.class, "reset", String.class, String.class, String[].class);
        Long currentTimeMillis = methodInvokerZero.apply(service, UUID.randomUUID().toString(), "Service Zero New Name", new String[] {"item 3", "item 4"});
        
        MultiParamsFunction<Long> methodInvokerOne = fIF.getOrCreate(Service.class, "reset", String.class, String[].class);
        currentTimeMillis = methodInvokerOne.apply(service, "Service One", new String[] {"item 1", "item 2"});
        
        BiFunction<Service, String[], Long> methodInvokerTwo = fIF.getOrCreate(Service.class, "reset", String[].class);
        currentTimeMillis = methodInvokerTwo.apply(service, new String[] {"Service Two"});
        
        BiFunction<Service, String, Long> methodInvokerThree = fIF.getOrCreate(Service.class, "reset", String.class);
        currentTimeMillis = methodInvokerThree.apply(service, "Service Three");
        
        Function<Service, Long> methodInvokerFour = fIF.getOrCreate(Service.class, "reset");
        currentTimeMillis = methodInvokerFour.apply(service);
    }
    
    private void testVoidMethodsBinding(Service service) {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsConsumer methodInvokerZero = fIF.getOrCreate(Service.class, "voidReset", String.class, String.class, String[].class);
        methodInvokerZero.accept(service, UUID.randomUUID().toString(), "Service Zero New Name", new String[] {"item 3", "item 4"});
        
        MultiParamsConsumer methodInvokerOne = fIF.getOrCreate(Service.class, "voidReset", String.class, String[].class);
        methodInvokerOne.accept(service, "Service One", new String[] {"item 1", "item 2"});
        
        BiConsumer<Service, String[]> methodInvokerTwo = fIF.getOrCreate(Service.class, "voidReset", String[].class);
        methodInvokerTwo.accept(service, new String[] {"Service Two"});
        
        BiConsumer<Service, String> methodInvokerThree = fIF.getOrCreate(Service.class, "voidReset", String.class);
        methodInvokerThree.accept(service, "Service Three");
        
        Consumer<Service> methodInvokerFour = fIF.getOrCreate(Service.class, "voidReset");
        methodInvokerFour.accept(service);
    }
    
    private void testMethodsWithBooleanReturnBinding(Service service) {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsPredicate methodInvokerZero = fIF.getOrCreate(Service.class, "resetWithBooleanReturn", String.class, String.class, String[].class);
        boolean executed = methodInvokerZero.test(service, UUID.randomUUID().toString(), "Service Zero New Name", new String[] {"item 3", "item 4"});
        
        MultiParamsPredicate methodInvokerOne = fIF.getOrCreate(Service.class, "resetWithBooleanReturn", String.class, String[].class);
        executed = methodInvokerOne.test(service, "Service One", new String[] {"item 1", "item 2"});
        
        BiPredicate<Service, String[]> methodInvokerTwo = fIF.getOrCreate(Service.class, "resetWithBooleanReturn", String[].class);
        executed = methodInvokerTwo.test(service, new String[] {"Service Two"});
        
        BiPredicate<Service, String> methodInvokerThree = fIF.getOrCreate(Service.class, "resetWithBooleanReturn", String.class);
        executed = methodInvokerThree.test(service, "Service Three");
        
        Predicate<Service> methodInvokerFour = fIF.getOrCreate(Service.class, "resetWithBooleanReturn");
        executed = methodInvokerFour.test(service);
    }
    
    private void testStaticMethodsBinding() {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsFunction<Long> methodInvokerZero = fIF.getOrCreate(Service.class, "staticDoSomething", String.class, String.class, String[].class);
        Long currentTimeMillis = methodInvokerZero.apply(UUID.randomUUID().toString(), "Service Zero New Name", new String[] {"item 3", "item 4"});
        
        BiFunction<String, String[], Long> methodInvokerOne = fIF.getOrCreate(Service.class, "staticDoSomething", String.class, String[].class);
        currentTimeMillis = methodInvokerOne.apply("Service One", new String[] {"item 1", "item 2"});
        
        Function<String[], Long> methodInvokerTwo = fIF.getOrCreate(Service.class, "staticDoSomething", String[].class);
        currentTimeMillis = methodInvokerTwo.apply(new String[] {"Service Two"});
        
        Function<String, Long> methodInvokerThree = fIF.getOrCreate(Service.class, "staticDoSomething", String.class);
        currentTimeMillis = methodInvokerThree.apply("Service Three");
        
        Supplier<Long> methodInvokerFour = fIF.getOrCreate(Service.class, "staticDoSomething");
        currentTimeMillis = methodInvokerFour.get();
    }
    
    private void testStaticVoidMethodsBinding() {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsConsumer methodInvokerZero = fIF.getOrCreate(Service.class, "staticVoidDoSomething", String.class, String.class, String[].class);
        methodInvokerZero.accept(UUID.randomUUID().toString(), "Service Zero New Name", new String[] {"item 3", "item 4"});
        
        BiConsumer<String, String[]> methodInvokerOne = fIF.getOrCreate(Service.class, "staticVoidDoSomething", String.class, String[].class);
        methodInvokerOne.accept("Service One", new String[] {"item 1", "item 2"});
        
        Consumer<String[]> methodInvokerTwo = fIF.getOrCreate(Service.class, "staticVoidDoSomething", String[].class);
        methodInvokerTwo.accept(new String[] {"Service Two"});
        
        Consumer<String> methodInvokerThree = fIF.getOrCreate(Service.class, "staticVoidDoSomething", String.class);
        methodInvokerThree.accept("Service Three");
        
        Runnable methodInvokerFour = fIF.getOrCreate(Service.class, "staticVoidDoSomething");
        methodInvokerFour.run();
    }
    
    private void testStaticMethodsWithBooleanReturnBinding() {
        ComponentContainer componentContainer = ComponentContainer.getInstance();
        FunctionalInterfaceFactory fIF = componentContainer.getFunctionalInterfaceFactory();
        
        MultiParamsPredicate methodInvokerZero = fIF.getOrCreate(Service.class, "staticDoSomethingWithBooleanReturn", String.class, String.class, String[].class);
        boolean executed = methodInvokerZero.test(UUID.randomUUID().toString(), "Service Zero New Name", new String[] {"item 3", "item 4"});
        
        BiPredicate<String, String[]> methodInvokerOne = fIF.getOrCreate(Service.class, "staticDoSomethingWithBooleanReturn", String.class, String[].class);
        executed = methodInvokerOne.test("Service One", new String[] {"item 1", "item 2"});
        
        Predicate<String[]> methodInvokerTwo = fIF.getOrCreate(Service.class, "staticDoSomethingWithBooleanReturn", String[].class);
        executed = methodInvokerTwo.test(new String[] {"Service Two"});
        
        Predicate<String> methodInvokerThree = fIF.getOrCreate(Service.class, "staticDoSomethingWithBooleanReturn", String.class);
        executed = methodInvokerThree.test("Service Three");
        
        Supplier<Boolean> methodInvokerFour = fIF.getOrCreate(Service.class, "staticDoSomethingWithBooleanReturn");
        executed = methodInvokerFour.get();
    }
    
    public static void main(String[] args) {
        Binder binder = new Binder();
        Service service = binder.testConstructorsBinding();
        binder.testMethodsBinding(service);
        binder.testVoidMethodsBinding(service);
        binder.testMethodsWithBooleanReturnBinding(service);
        binder.testStaticMethodsBinding();
        binder.testStaticVoidMethodsBinding();
        binder.testStaticMethodsWithBooleanReturnBinding();
    }
    
}

