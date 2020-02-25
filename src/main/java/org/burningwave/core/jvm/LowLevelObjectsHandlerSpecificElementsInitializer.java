package org.burningwave.core.jvm;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.burningwave.ManagedLogger;
import org.burningwave.Throwables;
import org.burningwave.core.Component;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
abstract class LowLevelObjectsHandlerSpecificElementsInitializer implements Component {
	LowLevelObjectsHandler lowLevelObjectsHandler;
	
	LowLevelObjectsHandlerSpecificElementsInitializer(LowLevelObjectsHandler lowLevelObjectsHandler) {
		this.lowLevelObjectsHandler = lowLevelObjectsHandler;
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			this.lowLevelObjectsHandler.unsafe = (Unsafe)theUnsafeField.get(null);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "Exception while retrieving unsafe");
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	
	void init() {
		initEmptyMembersArrays();
		initMembersRetrievers();
		initSpecificElements();
	}


	private void initEmptyMembersArrays() {
		lowLevelObjectsHandler.emtpyFieldsArray = new Field[]{};
		lowLevelObjectsHandler.emptyMethodsArray = new Method[]{};
		lowLevelObjectsHandler.emptyConstructorsArray = new Constructor<?>[]{};
	}
	
	public static void build(LowLevelObjectsHandler lowLevelObjectsHandler) {
		try (LowLevelObjectsHandlerSpecificElementsInitializer initializer =
				lowLevelObjectsHandler.jVMInfo.getVersion() > 8 ?
				new LowLevelObjectsHandlerSpecificElementsInitializer4Java9(lowLevelObjectsHandler):
				new LowLevelObjectsHandlerSpecificElementsInitializer4Java8(lowLevelObjectsHandler)) {
			initializer.init();
		}
	}
	
	private void initMembersRetrievers() {
		try {
			Lookup consulter = lowLevelObjectsHandler.consulterRetriever.retrieve(Class.class);
			MethodType methodType = MethodType.methodType(Field[].class, boolean.class);
			MethodHandle methodHandle = consulter.findSpecial(Class.class, "getDeclaredFields0", methodType, Class.class);
			lowLevelObjectsHandler.getDeclaredFieldsRetriever = (BiFunction<Class<?>, Boolean, Field[]>)LambdaMetafactory.metafactory(
				consulter, 
				"apply",
				MethodType.methodType(BiFunction.class),
				methodHandle.type().generic(),
				methodHandle,
				methodHandle.type().changeParameterType(1, Boolean.class)
			).getTarget().invokeExact();
			
			methodType = MethodType.methodType(Method[].class, boolean.class);
			methodHandle = consulter.findSpecial(Class.class, "getDeclaredMethods0", methodType, Class.class);
			lowLevelObjectsHandler.getDeclaredMethodsRetriever = (BiFunction<Class<?>, Boolean, Method[]>)LambdaMetafactory.metafactory(
				consulter,
				"apply",
				MethodType.methodType(BiFunction.class),
				methodHandle.type().generic(),
				methodHandle,
				methodHandle.type().changeParameterType(1, Boolean.class)
			).getTarget().invokeExact();
			
			methodType = MethodType.methodType(Constructor[].class, boolean.class);
			methodHandle = consulter.findSpecial(Class.class, "getDeclaredConstructors0", methodType, Class.class);
			lowLevelObjectsHandler.getDeclaredConstructorsRetriever = (BiFunction<Class<?>, Boolean, Constructor<?>[]>)LambdaMetafactory.metafactory(
				consulter,
				"apply",
				MethodType.methodType(BiFunction.class),
				methodHandle.type().generic(),
				methodHandle,
				methodHandle.type().changeParameterType(1, Boolean.class)
			).getTarget().invokeExact();
			lowLevelObjectsHandler.parentClassLoaderFields = new ConcurrentHashMap<>();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	abstract void initSpecificElements();
	
	@Override
	public void close() {
		this.lowLevelObjectsHandler = null;
	}
}
