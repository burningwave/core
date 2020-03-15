package org.burningwave.core.jvm;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

class LowLevelObjectsHandlerInitializer4Java8 extends LowLevelObjectsHandlerInitializer{

	LowLevelObjectsHandlerInitializer4Java8(LowLevelObjectsHandler lowLevelObjectsHandler) {
		super(lowLevelObjectsHandler);
		Field modes;
		try {
			modes = Lookup.class.getDeclaredField("allowedModes");
		} catch (NoSuchFieldException | SecurityException exc) {
			throw Throwables.toRuntimeException(exc);
		}
		modes.setAccessible(true);
		lowLevelObjectsHandler.consulterRetriever = (cls) -> {
			Lookup consulter = MethodHandles.lookup().in(cls);
			modes.setInt(consulter, -1);
			return consulter;
		};
	}

	@Override
	void initSpecificElements() {
		lowLevelObjectsHandler.packageRetriever = (classLoader, object, packageName) -> (Package)object;
		try {
			final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", AccessibleObject.class, boolean.class);
			accessibleSetterMethod.setAccessible(true);
			lowLevelObjectsHandler.accessibleSetter = (accessibleObject, flag) ->
				accessibleSetterMethod.invoke(null, accessibleObject, flag);
		} catch (Throwable exc) {
			logInfo("method setAccessible0 class not detected on " + AccessibleObject.class.getName());
			throw Throwables.toRuntimeException(exc);
		}
		try {
			lowLevelObjectsHandler.methodInvoker = Class.forName("sun.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
			lowLevelObjectsHandler.setAccessible(lowLevelObjectsHandler.methodInvoker, true);
		} catch (Throwable exc2) {
			logError("method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
			throw Throwables.toRuntimeException(exc2);
		}		
	}
}
